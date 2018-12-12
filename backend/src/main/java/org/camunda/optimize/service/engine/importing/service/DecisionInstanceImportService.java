package org.camunda.optimize.service.engine.importing.service;

import org.camunda.optimize.dto.engine.HistoricDecisionInputInstanceDto;
import org.camunda.optimize.dto.engine.HistoricDecisionInstanceDto;
import org.camunda.optimize.dto.engine.HistoricDecisionOutputInstanceDto;
import org.camunda.optimize.dto.optimize.importing.DecisionInstanceDto;
import org.camunda.optimize.dto.optimize.importing.InputInstanceDto;
import org.camunda.optimize.dto.optimize.importing.OutputInstanceDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.job.importing.DecisionInstanceElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.DecisionInstanceWriter;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.util.ProcessVariableHelper.isVariableTypeSupported;

public class DecisionInstanceImportService {
  private static final Logger logger = LoggerFactory.getLogger(DecisionInstanceImportService.class);

  protected ElasticsearchImportJobExecutor elasticsearchImportJobExecutor;
  protected String engineAlias;
  private DecisionInstanceWriter decisionInstanceWriter;
  private DecisionDefinitionVersionResolverService decisionDefinitionVersionResolverService;

  public DecisionInstanceImportService(DecisionInstanceWriter decisionInstanceWriter,
                                       ElasticsearchImportJobExecutor elasticsearchImportJobExecutor,
                                       EngineContext engineContext,
                                       DecisionDefinitionVersionResolverService decisionDefinitionVersionResolverService
  ) {
    this.elasticsearchImportJobExecutor = elasticsearchImportJobExecutor;
    this.engineAlias = engineContext.getEngineAlias();
    this.decisionInstanceWriter = decisionInstanceWriter;
    this.decisionDefinitionVersionResolverService = decisionDefinitionVersionResolverService;
  }

  public void executeImport(List<HistoricDecisionInstanceDto> engineDtoList) {
    logger.trace("Importing entities from engine...");
    boolean newDataIsAvailable = !engineDtoList.isEmpty();
    if (newDataIsAvailable) {
      final List<DecisionInstanceDto> optimizeDtos = mapEngineEntitiesToOptimizeEntities(engineDtoList);
      final ElasticsearchImportJob<DecisionInstanceDto> elasticsearchImportJob = createElasticsearchImportJob(
        optimizeDtos);
      addElasticsearchImportJobToQueue(elasticsearchImportJob);
    }
  }

  private void addElasticsearchImportJobToQueue(ElasticsearchImportJob elasticsearchImportJob) {
    try {
      elasticsearchImportJobExecutor.executeImportJob(elasticsearchImportJob);
    } catch (InterruptedException e) {
      logger.error("Was interrupted while trying to add new job to Elasticsearch import queue.", e);
    }
  }

  private List<DecisionInstanceDto> mapEngineEntitiesToOptimizeEntities(List<HistoricDecisionInstanceDto> engineEntities) {
    return engineEntities.stream()
      .map(this::mapEngineEntityToOptimizeEntity)
      .collect(Collectors.toList());
  }

  private ElasticsearchImportJob<DecisionInstanceDto> createElasticsearchImportJob(List<DecisionInstanceDto> decisionInstanceDtos) {
    final DecisionInstanceElasticsearchImportJob importJob = new DecisionInstanceElasticsearchImportJob(
      decisionInstanceWriter
    );
    importJob.setEntitiesToImport(decisionInstanceDtos);
    return importJob;
  }

  public DecisionInstanceDto mapEngineEntityToOptimizeEntity(HistoricDecisionInstanceDto engineEntity) {
    DecisionInstanceDto decisionInstanceDto = new DecisionInstanceDto();
    decisionInstanceDto.setDecisionInstanceId(engineEntity.getId());
    decisionInstanceDto.setProcessDefinitionKey(engineEntity.getProcessDefinitionKey());
    decisionInstanceDto.setProcessDefinitionId(engineEntity.getProcessDefinitionId());
    decisionInstanceDto.setDecisionDefinitionId(engineEntity.getDecisionDefinitionId());
    decisionInstanceDto.setDecisionDefinitionKey(engineEntity.getDecisionDefinitionKey());
    decisionInstanceDto.setDecisionDefinitionVersion(resolveDecisionDefinitionVersion(engineEntity));
    decisionInstanceDto.setEvaluationDateTime(engineEntity.getEvaluationTime());
    decisionInstanceDto.setProcessInstanceId(engineEntity.getProcessInstanceId());
    decisionInstanceDto.setRootProcessInstanceId(engineEntity.getRootProcessInstanceId());
    decisionInstanceDto.setActivityId(engineEntity.getActivityId());
    decisionInstanceDto.setCollectResultValue(engineEntity.getCollectResultValue());
    decisionInstanceDto.setRootDecisionInstanceId(engineEntity.getRootDecisionInstanceId());

    decisionInstanceDto.setInputs(
      engineEntity.getInputs().stream()
        .filter(this::isValidInputInstanceDto)
        .map(this::mapEngineInputDtoToOptimizeInputDto)
        .collect(Collectors.toList())
    );

    decisionInstanceDto.setOutputs(
      engineEntity.getOutputs().stream()
        .filter(this::isValidOutputInstanceDto)
        .map(this::mapEngineOutputDtoToOptimizeOutputDto)
        .collect(Collectors.toList())
    );

    decisionInstanceDto.setEngine(engineAlias);
    return decisionInstanceDto;
  }

  private String resolveDecisionDefinitionVersion(final HistoricDecisionInstanceDto engineEntity) {
    return decisionDefinitionVersionResolverService
      .getVersionForDecisionDefinitionId(engineEntity.getDecisionDefinitionId())
      .orElseThrow(() -> {
        final String message = String.format(
          "Couldn't obtain version for decisionDefinitionId [%s]. It hasn't been imported yet",
          engineEntity.getDecisionDefinitionId()
        );
        return new OptimizeRuntimeException(message);
      });
  }

  private InputInstanceDto mapEngineInputDtoToOptimizeInputDto(final HistoricDecisionInputInstanceDto engineInputDto) {
    return new InputInstanceDto(
      engineInputDto.getId(),
      engineInputDto.getClauseId(),
      engineInputDto.getClauseName(),
      engineInputDto.getType(),
      String.valueOf(engineInputDto.getValue())
    );
  }

  private OutputInstanceDto mapEngineOutputDtoToOptimizeOutputDto(final HistoricDecisionOutputInstanceDto engineOutputDto) {
    return new OutputInstanceDto(
      engineOutputDto.getId(),
      engineOutputDto.getClauseId(),
      engineOutputDto.getClauseName(),
      engineOutputDto.getRuleId(),
      engineOutputDto.getRuleOrder(),
      engineOutputDto.getVariableName(),
      engineOutputDto.getType(),
      String.valueOf(engineOutputDto.getValue())
    );
  }

  private boolean isValidInputInstanceDto(final HistoricDecisionInputInstanceDto inputInstanceDto) {
    if (isNullOrEmpty(inputInstanceDto.getType()) || !isVariableTypeSupported(inputInstanceDto.getType())) {
      logger.info(
        "Refuse to add input variable [{}] of type [{}]. Variable has no type or type is not supported.",
        inputInstanceDto.getId(),
        inputInstanceDto.getType()
      );
      return false;
    }
    return true;
  }


  private boolean isValidOutputInstanceDto(final HistoricDecisionOutputInstanceDto outputInstanceDto) {
    if (isNullOrEmpty(outputInstanceDto.getType()) || !isVariableTypeSupported(outputInstanceDto.getType())) {
      logger.info(
        "Refuse to add output variable [{}] of type [{}]. Variable has no type or type is not supported.",
        outputInstanceDto.getId(),
        outputInstanceDto.getType()
      );
      return false;
    }
    return true;
  }


  private boolean isNullOrEmpty(String value) {
    return value == null || value.isEmpty();
  }

}
