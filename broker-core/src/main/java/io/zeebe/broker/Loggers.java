package io.zeebe.broker;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Loggers
{

    public static final Logger CLUSTERING_LOGGER = LogManager.getLogger("io.zeebe.broker.clustering");
    public static final Logger SERVICES_LOGGER = LogManager.getLogger("io.zeebe.broker.services");
    public static final Logger SYSTEM_LOGGER = LogManager.getLogger("io.zeebe.broker.system");
    public static final Logger TRANSPORT_LOGGER = LogManager.getLogger("io.zeebe.broker.transport");

}
