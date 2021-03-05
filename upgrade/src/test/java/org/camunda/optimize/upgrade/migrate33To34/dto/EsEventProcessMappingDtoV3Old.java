/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.migrate33To34.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessRoleRequestDto;
import org.camunda.optimize.dto.optimize.query.event.process.es.EsEventMappingDto;

import java.time.OffsetDateTime;
import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class EsEventProcessMappingDtoV3Old {

  private String id;
  private String name;
  private String xml;
  private OffsetDateTime lastModified;
  private String lastModifier;
  private List<EsEventMappingDto> mappings;
  private List<EventSourceEntryDtoOld> eventSources;
  private List<EventProcessRoleRequestDto<IdentityDto>> roles;

}
