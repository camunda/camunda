package org.camunda.optimize.service.configuration;

import org.glassfish.hk2.api.Factory;

import java.io.IOException;
import java.util.Properties;

/**
 * @author Askar Akhmerov
 */
public class PropertiesProvider implements Factory<Properties> {

  private static Properties instance;

  @Override
  public Properties provide() {
    if (instance == null) {
      instance = new Properties();
      try {
        instance.load(this.getClass().getClassLoader().getResourceAsStream("service.properties"));
        //TODO: load extra properties specific to environment and overwrite defaults
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
    return instance;
  }

  @Override
  public void dispose(Properties properties) {

  }
}
