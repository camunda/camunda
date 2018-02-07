package org.camunda.optimize.service.security;

import org.camunda.optimize.dto.optimize.query.sharing.SharingDto;
import org.camunda.optimize.service.es.reader.SharingReader;
import org.camunda.optimize.service.es.writer.SharingWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * @author Askar Akhmerov
 */
@Component
public class SharingService {
  @Autowired
  private SharingWriter sharingWriter;

  @Autowired
  private SharingReader sharingReader;

  public String crateNewShare(SharingDto createSharingDto) {
    String result;
    Optional<SharingDto> existing = sharingReader.findSharedResource(createSharingDto);

    result = existing
      .map(SharingDto::getId)
      .orElseGet(() -> sharingWriter.saveShare(createSharingDto).getId());

    return result;
  }
}
