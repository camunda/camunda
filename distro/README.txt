This is a distribution of Operate ${project.version}

How to run
==========

Prerequisites:
1. Zeebe is running with Elasticsearch exporter turned on
2. Elasticsearch is running (it can be either the same instance configured for Zeebe export, or a separate one)

To run Operate:
1. Adjust Operate configuration file application.yml to point to your Zeebe and Elasticsearch instances
2. Run bin/operate or bin/operate.bat, depending on your file system.
