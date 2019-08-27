# Bucket4J-EhcacheTests
Some cache tests for Bucket4J

Build and assemble with maven
------------------------------

For Ehcache Terracotta-Clustered configuration:
```
mvn clean package appassembler:assemble -P ehcache2-ee-clustered
```

For Ehcache standalone configuration:
```
mvn clean package appassembler:assemble -P ehcache2-ee-standalone
```

Running the testers
-----------------------

Run bucket4j compatibity tests as explained at https://github.com/vladimir-bukhtoyarov/bucket4j/blob/master/doc-pages/production-jcache-checklist.md

```
bash target/appassembler/bin/CompatibilityTest
```

Run Simple Command Line Tester (make sure terracotta started on localhost with standard port 9510)

```
bash ./target/appassembler/bin/Launcher
```