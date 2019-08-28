# Bucket4J-EhcacheTests
Some cache tests for Bucket4J


## Build and assemble with maven

### Ehcache 2.x

For Ehcache Terracotta-Clustered configuration:
```
mvn clean package appassembler:assemble -P ehcache2-ee-clustered
```

For Ehcache standalone configuration:
```
mvn clean package appassembler:assemble -P ehcache2-ee-standalone
```

### Ehcache 3.x OSS

For Ehcache Terracotta-Clustered configuration:
```
mvn clean package appassembler:assemble -P ehcache3-oss-clustered
```

For Ehcache standalone configuration:
```
mvn clean package appassembler:assemble -P ehcache3-oss-standalone
```

### Ehcache 3.x EE

For Ehcache Terracotta-Clustered configuration:
```
mvn clean package appassembler:assemble -P ehcache3-ee-clustered
```

For Ehcache standalone configuration:
```
mvn clean package appassembler:assemble -P ehcache3-ee-standalone
```

## Start the Terracotta Server for the clustered configurations

### Terracotta for Ehcache2

Doc TBD

### Terracotta for Ehcache3 (OSS)

[Download](https://github.com/ehcache/ehcache3/releases) the full Ehcache clustering kit, if you have not.  This kit contains the Terracotta Server, which enables distributed caching with Ehcache.

Open a terminal and change into the directory where you have this sample.

Start the Terracotta Server, using the configuration supplied with this sample:

```bash
export ehcache_clustered_kit_home=/some/path
nohup $ehcache_clustered_kit_home/server/bin/start-tc-server.sh -f ./configs/ehcache3/tc-config.xml &
```

(For Windows environments, use the .bat start script rather than the .sh one).

Wait a few seconds for the Terracotta Server to start up - there will be a clear message in the terminal stating the server is *ACTIVE* and *ready for work*.

### Terracotta for Ehcache3 (Enterprise)

Doc TBD

## Running the testers

Run bucket4j compatibity tests as explained at https://github.com/vladimir-bukhtoyarov/bucket4j/blob/master/doc-pages/production-jcache-checklist.md

```
bash target/appassembler/bin/CompatibilityTest
```

Run Simple Command Line Tester (make sure terracotta started on localhost with standard port 9510)

```
bash ./target/appassembler/bin/Launcher
```