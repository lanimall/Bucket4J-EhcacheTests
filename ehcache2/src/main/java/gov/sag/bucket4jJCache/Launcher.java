package gov.sag.bucket4jJCache;

import io.github.bucket4j.*;
import io.github.bucket4j.grid.GridBucketState;
import io.github.bucket4j.grid.ProxyManager;
import io.github.bucket4j.grid.ehcache2.Ehcache2;
import net.sf.ehcache.Cache;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Arrays;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Launcher {
    public static final int DEFAULT_NBOFELEMENTS = 10;
    public static final String DEFAULT_BUCKET = "test";

    public static final int DEFAULT_LOOP_ITERATIONS = 10;
    public static final long DEFAULT_LOOP_SLEEPINTERVAL = 5000;
    public static final String LOOP_REGEX = "^\\{loop([^}]*)\\}";
    public static final String ARGS_SEPARATOR = " ";
    public static final String COMMAND_SEPARATOR = "~~";

    private static Logger log = LoggerFactory.getLogger(Launcher.class);
    Pattern loopPattern = Pattern.compile(LOOP_REGEX, Pattern.CASE_INSENSITIVE);

    public static final String ENV_BUCKET_CONFIG = "bucket.config.template";
    public static final String ENV_BUCKET_CONFIG_DEFAULT = "1";

    private static final BucketConfiguration configuration;

    static {
        int bucketConfigTemplate = Integer.parseInt(System.getProperty(ENV_BUCKET_CONFIG,ENV_BUCKET_CONFIG_DEFAULT));
        if(bucketConfigTemplate == 1)
            configuration = getConfiguration1();
        else if (bucketConfigTemplate == 2)
            configuration = getConfiguration2();
        else if (bucketConfigTemplate == 3)
            configuration = getConfiguration3();
        else
            throw new IllegalArgumentException("bucketConfigTemplate value not valid: " + bucketConfigTemplate);
    }

    private static BucketConfiguration getConfiguration1(){
        return Bucket4j.configurationBuilder()
                .addLimit(Bandwidth.simple(10, Duration.ofMinutes(1)))
                .build();
    }

    private static BucketConfiguration getConfiguration2(){
        // When refill created via "intervally" factory method then greediness is turned-off.
        Refill refill = Refill.intervally(10, Duration.ofMinutes(1));
        Bandwidth bandwidth = Bandwidth.classic(50, refill);
        return Bucket4j.configurationBuilder().addLimit(bandwidth).build();
    }

    // The bucket size is 50 calls (which cannot be exceeded at any given time),
    // with a "refill rate" of 10 calls per second that continually increases tokens in the bucket.
    private static BucketConfiguration getConfiguration3(){
        long overdraft = 50;
        Refill refill = Refill.greedy(10, Duration.ofMinutes(1));
        Bandwidth bandwidth = Bandwidth.classic(overdraft, refill);
        return Bucket4j.configurationBuilder().addLimit(bandwidth).build();
    }

    // cache for storing token buckets, where IP is key.
    private Ehcache cache;
    private ProxyManager<String> buckets;

    public Launcher() throws Exception {
        cache = EhcacheUtils.getCache();

        // init bucket registry
        buckets = Bucket4j.extension(Ehcache2.class).proxyManagerForCache((Cache)cache);
    }

    public static void main(String[] args) throws Exception {
        Launcher launcher = new Launcher();
        launcher.run(args);
        log.info("Completed");
        System.exit(0);
    }

    private static int operationIdCounter = 1;
    private enum OPERATIONS {
        OP_LOAD("New Requests (@@opInput@@ <number of requests> <bucket_key>)"),
        OP_GETCOUNT("Display cache entry (@@opInput@@ <bucket_key>)"),
        OP_QUIT("Quit program");

        private String opInput;
        private String opDetail;

        private OPERATIONS() {
            this(operationIdCounter++, "");
        }

        private OPERATIONS(String opDetail) {
            this(operationIdCounter++, opDetail);
        }

        private OPERATIONS(int opInput, String opDetail) {
            this(new Integer(opInput).toString(), opDetail);
        }

        private OPERATIONS(String opInput, String opDetail) {
            this.opInput = opInput;
            if (null != opDetail) {
                opDetail = opDetail.replaceAll("@@opInput@@", String.valueOf(opInput));
            }
            this.opDetail = opDetail;
        }

        public static OPERATIONS getById(String input) {
            for (OPERATIONS op : values()) {
                if (op.opInput.equalsIgnoreCase(input))
                    return op;
            }
            return null;
        }

        @Override
        public String toString() {
            return String.valueOf(opInput) + " - " + opDetail;
        }
    }

    public void run(String[] args) throws Exception {
        printLineSeparator();
        boolean keepRunning = true;

        String joinedCommand = null;
        if (null != args && args.length > 0) {
            //there could be several command chained together...hence let's try to find it out
            joinedCommand = joinStringArray(args, ARGS_SEPARATOR);
        }

        boolean interactive = false;
        while (keepRunning) {
            if(null == joinedCommand) {
                interactive = true;
                printOptions();
                joinedCommand = getInput();
                joinedCommand = joinedCommand.trim(); //remove the spaces
                if (joinedCommand.length() == 0) {
                    continue;
                }
            }

            boolean doLoop = false;
            String loopCommand = null;
            int loopIterations = DEFAULT_LOOP_ITERATIONS;
            long intervalMillis = DEFAULT_LOOP_SLEEPINTERVAL;;
            Matcher loopMatcher = loopPattern.matcher(joinedCommand);
            if (loopMatcher.find()) {
                doLoop = true;


                if(loopMatcher.groupCount() > 1){
                    loopCommand = loopMatcher.group(1);
                    loopCommand = loopCommand.trim();

                    //parse loopCommand
                    String[] loopArgs = loopCommand.split(" ");
                    if(loopArgs.length > 0) {
                        try {
                            loopIterations = Integer.parseInt(loopArgs[0]);
                        } catch (NumberFormatException nfe){
                            loopIterations = DEFAULT_LOOP_ITERATIONS;
                        }
                    }

                    if(loopArgs.length > 1) {
                        try {
                            intervalMillis = Long.parseLong(loopArgs[1]);
                        } catch (NumberFormatException nfe){
                            intervalMillis = DEFAULT_LOOP_SLEEPINTERVAL;
                        }
                    }
                }

                //remove the loop command from the main command
                joinedCommand = loopMatcher.replaceAll("");
                joinedCommand = joinedCommand.trim(); //remove the spaces
            }

            if (log.isInfoEnabled())
                log.info("Command: {} -- Loop: {} - Loop command: {}", joinedCommand, doLoop, (null!=loopCommand)?loopCommand:"null");


            String[] multipleCommands = joinedCommand.split(COMMAND_SEPARATOR);
            int currentIterations = 0;
            do {
                for (String inputCommand : multipleCommands) {
                    keepRunning = processInput(inputCommand);
                    if (!keepRunning)
                        break;

                    if(doLoop)
                        Thread.sleep(intervalMillis);
                }
                currentIterations++;
            } while (doLoop && currentIterations < loopIterations);

            //if args are specified directly, it should run once and exit (useful for batch scripting)
            if(!interactive)
                keepRunning = false;
            else
                joinedCommand = null;
        }
    }

    private String joinStringArray(String[] arr, String separator) {
        String join = "";
        if (null != arr) {
            for (String s : arr) {
                if (join.length() > 0)
                    join += separator;
                join += s;
            }
        }
        return join;
    }

    private String getInput() throws Exception {
        System.out.println(">>");

        // option1
        Scanner sc = new Scanner(System.in);
        sc.useDelimiter(System.getProperty("line.separator"));
        return sc.nextLine();
    }

    public boolean processInput(String input) throws Exception {
        String[] inputs = input.split(" ");
        return processInput(inputs);
    }

    public boolean processInput(String[] args) throws Exception {
        String[] inputArgs = null;
        String inputCommand = "";
        if (null != args && args.length > 0) {
            inputCommand = args[0];
            if (args.length > 1) {
                inputArgs = Arrays.copyOfRange(args, 1, args.length);
            }
        }

        boolean returnValue = false;
        try {
            //parsing operation
            OPERATIONS command = OPERATIONS.getById(inputCommand);
            if(null == command){
                log.info("Unrecognized command");
                return true;
            } else {
                returnValue = processInput(command, inputArgs);
            }
        } catch (Exception e) {
            log.error("Error", e);
        }

        return returnValue;
    }

    public void printLineSeparator() {
        String lineSeparator = System.getProperty("line.separator");
        byte[] bytes = lineSeparator.getBytes();
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b) + " ");
        }
        log.info("Line separator = " + lineSeparator + " (hex = " + sb.toString() + ")");
    }

    public void printOptions() {
        System.out.println("");
        System.out.println("");
        System.out.println("What do you want to do now?");
        for (OPERATIONS op : OPERATIONS.values()) {
            System.out.println(op.toString());
        }
    }

    private boolean processInput(OPERATIONS command, String[] args) throws Exception {
        log.info("######################## Processing command... ############################");

        //get the operation based on input
        log.info(String.format("Command: \"%s\" with params \"%s\"", command.opDetail, joinStringArray(args, ARGS_SEPARATOR)));
        try {
            switch (command) {
                case OP_LOAD:
                    //get params
                    int nbOfElements = DEFAULT_NBOFELEMENTS;
                    String bucketKey = DEFAULT_BUCKET;
                    if (null != args && args.length > 0) {
                        try {
                            nbOfElements = Integer.parseInt(args[0]);
                        } catch (NumberFormatException nfe) {
                            nbOfElements = DEFAULT_NBOFELEMENTS;
                        }

                        if (args.length > 1)
                            bucketKey = args[1];
                    }

                    // acquire proxy to bucket
                    Bucket bucket = buckets.getProxy(bucketKey, configuration);

                    long start = System.nanoTime();

                    int submitCount = 0;
                    while (submitCount < nbOfElements) {
                        // tryConsume returns false immediately if no tokens available with the bucket
                        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
                        if (probe.isConsumed()) {
                            System.out.println(String.format("The rate limit is NOT exceeded. %s", probe.toString()));
                        } else {
                            System.out.println(String.format("The rate limit IS exceeded. Remaining tokens: %d. Time to wait for next refill: %d ms", probe.getRemainingTokens(), probe.getNanosToWaitForRefill()/1000/1000));
                        }

                        submitCount++;
                    }

                    displayTiming("Operation loadCache()", start);

                    break;
                case OP_GETCOUNT:
                    String cacheKey = null;
                    if (null != args && args.length > 0) {
                        cacheKey = args[0];
                    }

                    displayCacheElement(cacheKey, true, false);
                    break;
                case OP_QUIT:
                    return false;
                default:
                    log.info(String.format("Unrecognized command: %s %s", command.opInput, joinStringArray(args, ARGS_SEPARATOR)));
                    break;
            }
        } catch (Exception e) {
            log.error("Exception occurred", e);
        }

        log.info("#########################################################################");
        return true;
    }

    private void displayCacheElement(String keyToGet, boolean displayElements, boolean displayTiming) {
        Object valueObj = get(keyToGet, displayTiming);

        BucketState bucketState = null;
        if(null != valueObj && valueObj instanceof GridBucketState){
            bucketState = ((GridBucketState)valueObj).getState();
        }

        if (displayElements) {
            if (null != bucketState) {
                log.info("Key=[" + keyToGet.toString() + "] - value=[" + bucketState.toString() + "]");
            } else {
                log.info("Key=["  + keyToGet.toString() + "]=null");
            }
        }
    }

    private Object get(String key, boolean displayTiming) {
        long start = System.nanoTime();
        Element elem = cache.get(key);
        if (displayTiming)
            displayTiming("Operation cache.get(key)", start);

        return (null != elem)?elem.getObjectValue():null;
    }

    private void displayTiming(String prefix, long startTimeNanos) {
        log.info(String.format("%s - Duration: %.3f ms", prefix, (System.nanoTime() - startTimeNanos) / 1000000F));
    }
}