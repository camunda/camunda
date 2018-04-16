package org.camunda.optimize.upgrade.configuration;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.ReadContext;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.json.JsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import com.jayway.jsonpath.spi.mapper.MappingProvider;
import org.camunda.optimize.upgrade.exception.UpgradeRuntimeException;
import org.camunda.optimize.upgrade.service.ValidationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * @author Askar Akhmerov
 */
public class ConfigurationService {
  private static final String ENVIRONMENT_CONFIG_YAML_REL_PATH = "/../environment/environment-config.yaml";
  private static final String[] DEFAULT_LOCATIONS = { ENVIRONMENT_CONFIG_YAML_REL_PATH };

  private ObjectMapper objectMapper = new ObjectMapper();
  private HashMap defaults = null;
  private ReadContext jsonContext;

  private final Logger logger = LoggerFactory.getLogger(getClass());

  private String clientHost;
  private Integer clientPort;
  private String dateFormat;

  public ConfigurationService() {
    this((String[]) null);
  }

  public ConfigurationService(String[] locations) {
    String[] locationsToUse = locations == null ? DEFAULT_LOCATIONS : locations;

    //prepare streams for locations
    List<InputStream> sources = new ArrayList<>();
    for (String location : locationsToUse) {
      InputStream inputStream = wrapInputStream(location);
      if (inputStream != null) {
        sources.add(inputStream);
      }
    }

    initFromStreams(sources);
  }

  public ConfigurationService(List<InputStream> sources) {
    initFromStreams(sources);
  }

  public void initFromStreams(List<InputStream> sources) {
    objectMapper = new ObjectMapper(new YAMLFactory());
    objectMapper.configure(JsonParser.Feature.ALLOW_COMMENTS,true);
    //read default values from the first location
    try {
      //configure Jackson as provider in order to be able to use TypeRef objects
      //during serialization process
      Configuration.setDefaults(new Configuration.Defaults() {

        private final JsonProvider jsonProvider = new JacksonJsonProvider();
        private final MappingProvider mappingProvider = new JacksonMappingProvider();

        @Override
        public JsonProvider jsonProvider() {
          return jsonProvider;
        }

        @Override
        public MappingProvider mappingProvider() {
          return mappingProvider;
        }

        @Override
        public Set<Option> options() {
          return EnumSet.noneOf(Option.class);
        }
      });

      JsonNode resultNode = objectMapper.readTree(sources.remove(0));
      //read with overriding default values all locations
      for (InputStream inputStream : sources) {
        merge(resultNode, objectMapper.readTree(inputStream));
      }

      defaults = objectMapper.convertValue(resultNode, HashMap.class);
    } catch (IOException e) {
      logger.error("error reading configuration", e);
    }

    //prepare to work with JSON Path
    jsonContext = JsonPath.parse(defaults);
  }

  public static JsonNode merge(JsonNode mainNode, JsonNode updateNode) {

    Iterator<String> fieldNames = updateNode.fieldNames();
    while (fieldNames.hasNext()) {

      String fieldName = fieldNames.next();
      JsonNode jsonNode = mainNode.get(fieldName);
      // if field exists and is an embedded object
      if (jsonNode != null && jsonNode.isObject()) {
        merge(jsonNode, updateNode.get(fieldName));
      } else if (mainNode instanceof ObjectNode) {
        // Overwrite field
        overwriteField((ObjectNode) mainNode, updateNode, fieldName);
      }

    }

    return mainNode;
  }


  private static void overwriteField(ObjectNode mainNode, JsonNode updateNode, String fieldName) {
    JsonNode value = updateNode.get(fieldName);
    mainNode.put(fieldName, value);
  }

  private InputStream wrapInputStream(String location) {
    String executionFolderPath = null;
    try {
      executionFolderPath = getClass().getProtectionDomain().getCodeSource().getLocation().toURI().getPath();
      executionFolderPath = executionFolderPath.substring(0, executionFolderPath.lastIndexOf("/"));
      executionFolderPath = executionFolderPath.replaceAll("%20"," ");

      File config = new File(executionFolderPath + location);
      return new FileInputStream(config);
    } catch (FileNotFoundException e) {
      String errorMessage = "can't read configuration for " + location + " relative to " + executionFolderPath;
      logger.error(errorMessage);
      throw new UpgradeRuntimeException(errorMessage);
    } catch (URISyntaxException e) {
      String errorMessage = "can't read configuration for " + location + " relative to " + executionFolderPath;
      logger.error(errorMessage);
      throw new UpgradeRuntimeException(errorMessage);
    }
  }

  public String getClientHost() {
    if (clientHost == null) {
      clientHost = jsonContext.read("es.host");
    }
    return clientHost;
  }

  public int getClientPort() {
    if (clientPort == null) {
      clientPort = jsonContext.read("es.port");
    }
    return clientPort;
  }

  public String getDateFormat() {
    if (dateFormat == null) {
      dateFormat = jsonContext.read("serialization.optimizeDateFormat");
    }
    return dateFormat;
  }
}
