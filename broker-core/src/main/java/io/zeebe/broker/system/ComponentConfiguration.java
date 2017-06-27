package io.zeebe.broker.system;

public class ComponentConfiguration
{
    public void applyGlobalConfiguration(GlobalConfiguration globalConfig)
    {
        // noop;
    }

    protected String getOrDefault(String configuredValue, String defaultValue)
    {
        if (configuredValue == null || configuredValue.isEmpty())
        {
            return defaultValue;
        }
        else
        {
            return configuredValue;
        }
    }
}
