package org.camunda.optimize.upgrade;

import org.camunda.optimize.upgrade.service.ValidationService;

import java.io.File;
import java.io.FileWriter;
import java.net.URISyntaxException;

public class EnvironmentConfigUtil {

  private EnvironmentConfigUtil() {
    super();
  }

  public static void createEmptyEnvConfig() throws Exception {
    createEnvConfig("");
  }


  public static void createEnvConfig(String content) throws Exception {
    File env = EnvironmentConfigUtil.createEnvFolder();
    File config = new File(env.getAbsolutePath() + "/environment-config.yaml");

    if (!config.exists()) {
      config.createNewFile();
      FileWriter fileWriter = new FileWriter(config);
      if (content != null && !content.isEmpty()) {
        fileWriter.append(content);
      } else {
        fileWriter.write("");
      }
      fileWriter.close();
    }
  }

  public static File createEnvFolder() throws Exception {
    File env = getEnvFolder();
    if (!env.exists()) {
      env.mkdirs();
    }
    return env;
  }

  public static void deleteEnvFolderWithConfig() throws Exception {
    deleteEnvConfig();
    deleteEnvFolder();
  }

  public static void deleteEnvFolder() throws Exception {
    File env = getEnvFolder();
    if (env.exists()) {
      env.delete();
    }
  }

  public static void deleteEnvConfig() throws Exception {
    File env = getEnvFolder();
    File config = new File(env.getAbsolutePath() + "/environment-config.yaml");

    if (config.exists()) {
      config.delete();
    }
  }

  public static File getEnvFolder() throws URISyntaxException {
    String executionFolderPath =
      ValidationService.class.
        getProtectionDomain()
        .getCodeSource()
        .getLocation()
        .toURI()
        .getPath();
    return new File(executionFolderPath + "/../environment");
  }
}