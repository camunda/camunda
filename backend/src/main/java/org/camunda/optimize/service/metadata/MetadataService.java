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

  private final String rawVersion;
  private final String version;

  public MetadataService() {
    this.rawVersion = Version.RAW_VERSION;
    this.version = Version.VERSION;
  }

  @PostConstruct
  public void initVersion() {
    if (configurationService.getCheckMetadata()) {
      Optional<MetadataDto> data = metadataReader.readMetadata();
      data.map((metadataDto) -> {
        if (!this.version.equals(metadataDto.getSchemaVersion())) {
          throw new OptimizeRuntimeException("The Elasticsearch data structure doesn't match the current Optimize version");
        }
        return null;
      }).orElseGet(() -> {
        metadataWriter.writeMetadata(initMetadata());
        return null;
      });
    }
  }

  private MetadataDto initMetadata() {
    MetadataDto result = new MetadataDto();
    result.setSchemaVersion(this.version);
    return result;
  }

  public String getVersion() {
    return version;
  }

  public String getRawVersion() {
    return rawVersion;
  }
}
