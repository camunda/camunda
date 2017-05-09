package org.camunda.tngp.broker.system;

import com.moandjiezana.toml.Toml;

import java.util.function.*;

public class ComponentConfiguration
{
    public class Rules
    {
        public String ruleName = "";
        public Object globalObj = null;
        public Object localObj = null;
        public String localName = "";
        public UnaryOperator<Object> rule = null;

        public Rules(String name)
        {
            this.ruleName = name;
        }
        public Rules setGlobalObj(Object global)
        {
            this.globalObj = global;
            return this;
        }
        public Rules setLocalObj(Object local, String name)
        {
            this.localObj = local;
            this.localName = name;
            return this;
        }
        public Rules setRule(UnaryOperator<Object> execRule)
        {
            this.rule = execRule;
            return this;
        }
        public Object execute()
        {
            if (this.globalObj != null && (tomlHandler == null || !tomlHandler.contains(this.localName)))
            {
                return this.rule.apply(this.globalObj);
            }
            else
            {
                return this.localObj;
            }
        }

    }


    public String globalDataPath = "";
    protected Toml tomlHandler = null;
    protected GlobalConfiguration globalConfiguration = null;

    //used in configuration manager
    public boolean applyGlobalConfiguration(Toml tomlHandler, GlobalConfiguration global)
    {

        this.tomlHandler = tomlHandler;
        this.globalConfiguration = global;
        onApplyingGlobalConfiguration(global);
        afterApplyingGlobalConfiguration();
        return true;
    }

    //will be override by subclasses, be called after applying the configuration replace rules
    public void afterApplyingGlobalConfiguration()
    {
    }

    //will be override each subclass
    protected void onApplyingGlobalConfiguration(GlobalConfiguration global)
    {
    }

}
