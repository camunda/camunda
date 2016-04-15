package org.camunda.tngp.broker.system;

import java.util.List;

public interface ConfigurationManager
{
    <T> T readEntry(String componentName, Class<T> configObjectType);

    <T> List<T> readList(String string, Class<T> class1);
}
