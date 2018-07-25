package org.camunda.optimize.service.metadata;

import org.camunda.optimize.dto.optimize.query.MetadataDto;
import org.camunda.optimize.service.es.reader.MetadataReader;
import org.camunda.optimize.service.es.writer.MetadataWriter;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Optional;


@Component
public class MetadataService {

  @Autowired
  private MetadataReader metadataReader;

  @Autowired
  private MetadataWriter metadataWriter;

  @Autowired
  private ConfigurationService configurationService;

  private final String version;

  public MetadataService() {
    this.version = removeAppendixFromVersion(Version.VERSION);;
  }

  @PostConstruct
  public void initVersion() {
    if (configurationService.getCheckMetadata()) {
      Optional<MetadataDto> data = metadataReader.readMetadata();
      data.map((metadataDto) -> {
        if (!this.version.equals(metadataDto.getSchemaVersion())) {
          throw new OptimizeRuntimeException("Optimize version is not matching schema");
        }
        return null;
      }).orElseGet(() -> {
        metadataWriter.writeMetadata(initMetadata());
        return null;
      });
    }
  }

  private static String removeAppendixFromVersion(String versionWithAppendix) {
    // The version might have an appendix like 2.2.0-SNAPSHOT
    int indexOfMinus = versionWithAppendix.indexOf("-");
    indexOfMinus = indexOfMinus == -1 ? versionWithAppendix.length() : indexOfMinus;
    return versionWithAppendix.substring(0, indexOfMinus);
  }

  private MetadataDto initMetadata() {
    MetadataDto result = new MetadataDto();
    result.setSchemaVersion(this.version);
    return result;
  }

  public String getVersion() {
    return version;
  }
}
