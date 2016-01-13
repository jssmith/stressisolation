*For a more extensive introduction and for discussion of the evaluation please also see this [blog post](https://johann.schleier-smith.com/blog/2016/01/06/analyzing-a-read-only-transaction-anomaly-under-snapshot-isolation.html).*

# Read Anomalies Under Snapshot Isolation #

The big idea in [snapshot isolation](https://en.wikipedia.org/wiki/Snapshot_isolation) is that transactions read the database with a certain notion of consistency:
transactions that committed before are visible, and those that completed after are not.
It's surprising, then, to learn that databases running under snapshot isolation can return results that could never have existed in the database.
This revelation, described by Alan Fekete, Elizabeth O'Neil, and Patrick O'Neil in their 2004 paper,
“[A read-only transaction anomaly under snapshot isolation](http://www.cs.umb.edu/~poneil/ROAnom.pdf),”
came to light only after this approach to consistency had gained widespread adoption in commercial databases.

Now that over 10 years have elapsed we ask two questions:

1. Are today's relational databases still subject to such surprising anomalies?
2. How likely are such read anomalies to occur?

This project contains a Scala program designed to answer these questions. It implements example 1.3 from Fekete, *et at.*, and works with Oracle, DB2, PostgreSQL, and MySQL.
Briefly put, this example shows that Oracle, even under the strongest isolation settings (SERIALIZABLE), still exhibits anomalous reads.
PostgreSQL appears to do so as well, under limited circumstances involving retries.
Less surprisingly, Oracle, PostgreSQL, and DB2 all exhibit the anomaly under less stringent (READ COMMITTED) isolation settings.
MySQL, as tested, does not support snapshot isolation and so exhibits no anomalies.

Note that this test seeks to evaluate consistency, not to performance in terms of throughput or response time.
Thus, this code does not assess the main benefit of snapshot isolation, which is to make the database faster. 

# Running the Tests #

In order to run the test you will need a Scala environment supporting [sbt](http://www.scala-sbt.org/) for running the driver program, plus
database instances to use as targets.
Docker instances provide a practical way to get up and running with databases quickly.

These instructions should work on both Linux and OS X.

## Stress Testing Client ##

If you have a database available up and running you can get started right away with the client.
If not, you will want to follow the [database configuration](#database-configuration) steps below.


### Configuring database connectivity ###

After checking out this repository, update the file src/main/resources/stressisolation.properties to reflect the
settings of your environment.

A snippet of the configuration for MySQL appears below.
Update the connection URL, username, and password to match your environment.
Please note that when running databases under docker on OS X you need to use the IP address of the virtual machine running Docker (run `docker-machine ls` to find it).
 
```
mysql.url = jdbc:mysql://192.168.99.100:32768/
mysql.user = root
mysql.pass = yourpassword
```

After configuring the database, launch sbt to test the connection. Within sbt, run the command

```
runMain stressisolation.exec.TestConnection Oracle
```

You should get something like the following output

```
Johanns-MacBook-Pro:StressDatabaseIsolation johann$ sbt
[info] Loading project definition from /Users/johann/dev/ext/StressDatabaseIsolation/project
[info] Set current project to StressDatabaseIsolation (in build file:/Users/johann/dev/ext/StressDatabaseIsolation/)
> runMain stressisolation.exec.TestConnection Oracle
[info] Compiling 4 Scala sources to /Users/johann/dev/ext/StressDatabaseIsolation/target/scala-2.11/classes...
[info] Running stressisolation.exec.TestConnection Oracle
connect to jdbc:oracle:thin:@192.168.99.100:49161:xe
1
test complete for Oracle
[success] Total time: 12 s, completed Jan 5, 2016 8:55:03 AM
> 
```

Substitute one of `MySql`, `Postgresql`, or `DB2` in place of `Oracle` to test connections to other databases.
Drivers for MySQL and PostgreSQL come automatically through sbt.
Due to licensing and distribution restrictions you will need to download the Oracle (ojdbc14.jar, ojdbc7.jar, or ojdbc7.jar) and DB2 (db2jcc.jar, or db2jcc4.jar) JDBC drivers yourself,
placing them in the lib/ directory. 

### Creating schema ###

The test programs require a schema that consists of two tables, one for checking and one for savings.
The following sbt command will initialize the database with the necessary schema

```
runMain stressisolation.exec.SetupStressTest Oracle
```

Again, substitute the name of other databases in place of `Oracle` to complete configuration.

### Running the stress test ###

The stress test program takes the following parameters:

- *Database vendor*: One of `Oracle`, `MySql`, `Postgresql`, or `DB2`.
- *Number of connections*: How many concurrent connections to make to the database.
- *Number of iterations*: How many sets of concurrent queries to issues (max 100,000).
  Each set of queries runs on a unique customer identifier.
- *Isolation level*: `READ_COMMITTED` and `SERIALIZABLE` are supported for all databases. Some may permit variants such as `READ_UNCOMMITTED` or `REPEATABLE_READ`.
- *Autocommit*: `false` for explicit commit commands, `true` for automatic commit with updates. 

Example command:

```
runMain stressisolation.exec.RunStressTest Oracle 50 1000 READ_COMMITTED false
```

In this example each customer has two accounts, one for checking and one for savings.
The stress test starts by zeroing out all account balances.
It then issues three queries concurrently with the following logic:

- *Txn 1*: Add 20 to savings.
- *Txn 2*: Subtract 10 from checking, but if doing so causes (checking + savings) to become negative charge an overdraft fee of 1.
- *Txn 3*: Read balances (checking, savings).

If *Txn 1* comes before *Txn 2* in a serial order the balances (checking, savings) end up as (20,-10),
leaving an ending total balance of 10,
whereas if *Txn 2* comes before *Txn 1* then (checking, savings) ends up as (20, -11),
leaving an ending total balance of 9.
If *Txn 3* observes a total balance of 20, then we should be able to infer that *Txn 1* preceded *Txn 2*, and
later queries should see a balance of 10.
We call the behavior anomalous if future queries instead see 9, suggesting that *Txn 2* has preceded *Txn 1*.
 
Here is the output of an example sample run:

```
2016-01-05 09:13:15,697 [run-main-4] INFO  stressisolation.exec.RunStressTest$ - progress 900
2016-01-05 09:13:15,999 [run-main-4] INFO  stressisolation.exec.RunStressTest$ - progress 1000
2016-01-05 09:13:16,029 [run-main-4] INFO  stressisolation.exec.RunStressTest$ - waiting on shutdown
2016-01-05 09:13:16,185 [run-main-4] INFO  stressisolation.exec.RunStressTest$ - threads finished
3819 tests/sec
Outcome Statistics
Expected: 977
ReadAnomaly: 23
UpdateAnomaly: 0
OtherAnomaly: 0
Failures: 0
Retries: 0
Retries also read anomalies 0
2016-01-05 09:13:17,635 [run-main-4] INFO  stressisolation.stats - Oracle,30,1000,READ_COMMITTED,false,3819,977,23,0,0,0,0,0
[success] Total time: 6 s, completed Jan 5, 2016 9:13:17 AM
> 
```

Outcome statistics describing correctness:
- *Expected*: No anomalous behavior.
- *ReadAnomaly*: Concurrent read in *Txn 3* shows (checking, savings) as (0, 20), whereas the final state is (-11, 20), meaning *Txn 3* shows a result inconsistent with the eventual ordering of *Txn 1* and *Txn2*.
- *UpdateAnomaly*: Final balance in checking or savings is 0. This means that one or both of the updates failed to execute. 
- *OtherAnomaly*: Any other outcome.

Other outcome counters:
- *Failures*: Number of transactions not executed, despite repeated retries (30 in current configuration). 
- *Retries*: Number of transaction retry attempts. Databases may reject transactions on account of concurrency,
  in which case this test harness will sleep for a little while and retry.
- *Retries also read anomalies*: The number of retries that also resulted in read anomalies.

You can customize logging by editing the configuration in src/main/resources/log4j.properties.
For example, you can collect run statistics in csv format in a log file by capturing the 
`stressisolation.stats` logger.

# </a>Database Configuration #

Installing and configuring databases can be a painful experience.
Luckily containers can make doing so much easier.  

## Start Docker ##

Getting up and running with docker on an Amazon EC2 instance (Amazon AMI) is quick and easy:

```
sudo yum install docker
sudo service docker start
```

For OS X install [Docker Machine](https://docs.docker.com/machine/install-machine/).
Note that for OS X you will not need to prefix the docker commands described below with sudo.

## Start database images ##


### MySQL ###

Use the [MySQL image](https://hub.docker.com/_/mysql/).
At the time of this writing the latest version is 5.7.
Please use your own password.

```
sudo docker run --name si-mysql -e MYSQL_ROOT_PASSWORD=yourpassword -P -d mysql:latest
```

### PostgreSQL ###

Use the [PostgreSQL image](https://hub.docker.com/_/postgres/).
At the time of this writing the latest version is 9.5.
Please use your own password.

```
sudo docker run --name si-postgres -e POSTGRES_PASSWORD=yourpassword -P -d postgres:latest
```

### Oracle ###

Use this [Oracle Express Edition 11g Release 2 image](https://hub.docker.com/r/wnameless/oracle-xe-11g/).
This image comes pre-configured with the administrative username/password combination system/oracle.

```
sudo docker run --name si-oracle -p 49160:22 -p 49161:1521 -d wnameless/oracle-xe-11g:latest
```

### DB2 ###

Use this [DB2 Express-C image](https://hub.docker.com/r/angoca/db2-sample/).
At the time of this writing the latest version is 10.5.
Launching DB2 requires just a few additional steps.

First run the container: 
```
sudo docker run -i -t --privileged=true --name=si-db2 -p 50000:50000 angoca/db2-sample:latest
```

Within the container start the database:

```
su -c "db2start" - db2inst1
```

Set the credentials for the db2inst1 user.
These are the credentials you will use to connect to the database

```
passwd db2inst1
```

Disconnect your terminal from the Docker instance by issuing the sequence *ctrl*+p, *ctrl*+q.

## Review images ##

Confirm that the Docker images are running properly and review the ports exposed:

```
docker ps
```

Sample output:

```
CONTAINER ID        IMAGE                            COMMAND                  CREATED             STATUS              PORTS                                                      NAMES
be4e413508a0        angoca/db2-sample:latest         "/bin/bash"              6 minutes ago        Up 12 hours         0.0.0.0:50000->50000/tcp                                   si-db2
ccad29085117        postgres:latest                  "/docker-entrypoint.s"   35 minutes ago      Up 35 minutes       0.0.0.0:32769->5432/tcp                                    si-postgres
45083cc64ecd        wnameless/oracle-xe-11g:latest   "/bin/sh -c '/usr/sbi"   39 minutes ago      Up 39 minutes       8080/tcp, 0.0.0.0:49160->22/tcp, 0.0.0.0:49161->1521/tcp   si-oracle
a060f99a2435        mysql:latest                     "/entrypoint.sh mysql"   40 minutes ago      Up 40 minutes       0.0.0.0:32768->3306/tcp                                    si-mysql
```
## Start testing ##

Now that you have the databases configured you're ready to [run the tests](#running-the-tests).
