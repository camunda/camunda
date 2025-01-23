This is a distribution of

       Camunda Optimize v${project.version}

visit
       https://docs.camunda.io/optimize/components/what-is-optimize/

==================

Contents:

        config/

                    this is a local environment folder that contains configuration properties,
                    which can be used to overwrite default values of Optimize configuration. Also the logging
                    level can be configured here.

        upgrade/

                    execute the upgrade optimize jar files from here to upgrade Optimize.

        optimize-startup.sh (unix) or optimize-startup.bat (windows)

                    a script to start elasticsearch and optimize in embedded Jetty container.

==================

Camunda Optimize version: ${project.version}
Elasticsearch version: ${version.elasticsearch}

=================
