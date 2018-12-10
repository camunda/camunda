package org.camunda.optimize.service.es.filter.decision;

import com.google.common.collect.Lists;
import org.camunda.optimize.dto.engine.DecisionDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.InputVariableEntry;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionReportResultDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineDatabaseRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.camunda.optimize.test.util.DecisionReportDataBuilder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.util.HashMap;

import static java.util.stream.Collectors.toMap;
import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.test.util.DecisionFilterUtilHelper.createFixedDateInputVariableFilter;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNull.notNullValue;

public class DecisionDateVariableFilterIT {

  private static final String INPUT_AMOUNT_ID = "clause1";
  private static final String INPUT_CATEGORY_ID = "InputClause_15qmk0v";
  private static final String INPUT_INVOICE_DATE_ID = "InputClause_0qixz9e";

  private static final String INPUT_VARIABLE_INVOICE_CATEGORY = "invoiceCategory";
  private static final String INPUT_VARIABLE_AMOUNT = "amount";
  private static final String INPUT_VARIABLE_INVOICE_DATE = "invoiceDate";

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();

  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();
  public EngineDatabaseRule engineDatabaseRule = new EngineDatabaseRule();

  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule)
    .around(engineRule)
    .around(embeddedOptimizeRule)
    .around(engineDatabaseRule);


  @Test
  public void resultFilterByGreaterThanDateInputVariable() {
    // given
    final OffsetDateTime dateTimeInputFilterStart = OffsetDateTime.parse("2019-01-01T00:00:00+00:00");
    final String inputVariableIdToFilterOn = INPUT_INVOICE_DATE_ID;

    final DecisionDefinitionEngineDto decisionDefinitionDto = engineRule.deployDecisionDefinition(
      "dmn/invoiceBusinessDecision_withDate.xml");
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputs(100.0, "2018-01-01T00:00:00+00:00")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputs(200.0, "2019-06-06T00:00:00+00:00")
    );

    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    DecisionReportDataDto reportData = DecisionReportDataBuilder.createDecisionReportDataViewRawAsTable(
      decisionDefinitionDto.getKey(), ALL_VERSIONS
    );
    reportData.setFilter(Lists.newArrayList(createFixedDateInputVariableFilter(
      inputVariableIdToFilterOn, dateTimeInputFilterStart, null
    )));
    RawDataDecisionReportResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getDecisionInstanceCount(), is(1L));
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult().size(), is(1));

    assertThat(
      (String) result.getResult().get(0).getInputVariables().get(inputVariableIdToFilterOn).getValue(),
      startsWith("2019-06-06T00:00:00")
    );
  }

  @Test
  public void resultFilterByLessThanDateInputVariable() {
    // given
    final OffsetDateTime dateTimeInputFilterEnd = OffsetDateTime.parse("2019-01-01T00:00:00+00:00");
    final String inputVariableIdToFilterOn = INPUT_INVOICE_DATE_ID;

    final DecisionDefinitionEngineDto decisionDefinitionDto = engineRule.deployDecisionDefinition(
      "dmn/invoiceBusinessDecision_withDate.xml");
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputs(100.0, "2018-01-01T00:00:00+00:00")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputs(200.0, "2019-06-06T00:00:00+00:00")
    );

    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    DecisionReportDataDto reportData = DecisionReportDataBuilder.createDecisionReportDataViewRawAsTable(
      decisionDefinitionDto.getKey(), ALL_VERSIONS
    );
    reportData.setFilter(Lists.newArrayList(createFixedDateInputVariableFilter(
      inputVariableIdToFilterOn, null, dateTimeInputFilterEnd
    )));
    RawDataDecisionReportResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getDecisionInstanceCount(), is(1L));
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult().size(), is(1));

    assertThat(
      (String) result.getResult().get(0).getInputVariables().get(inputVariableIdToFilterOn).getValue(),
      startsWith("2018-01-01T00:00:00")
    );
  }

  @Test
  public void resultFilterByDateRangeInputVariable() {
    // given
    final OffsetDateTime dateTimeInputFilterStart = OffsetDateTime.parse("2019-01-01T00:00:00+00:00");
    final OffsetDateTime dateTimeInputFilterEnd = OffsetDateTime.parse("2019-02-01T00:00:00+00:00");
    final String inputVariableIdToFilterOn = INPUT_INVOICE_DATE_ID;

    final DecisionDefinitionEngineDto decisionDefinitionDto = engineRule.deployDecisionDefinition(
      "dmn/invoiceBusinessDecision_withDate.xml");
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputs(100.0, "2018-01-01T00:00:00+00:00")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputs(200.0, "2019-01-01T01:00:00+00:00")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputs(300.0, "2019-06-06T00:00:00+00:00")
    );

    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    DecisionReportDataDto reportData = DecisionReportDataBuilder.createDecisionReportDataViewRawAsTable(
      decisionDefinitionDto.getKey(), ALL_VERSIONS
    );
    reportData.setFilter(Lists.newArrayList(createFixedDateInputVariableFilter(
      inputVariableIdToFilterOn, dateTimeInputFilterStart, dateTimeInputFilterEnd
    )));
    RawDataDecisionReportResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getDecisionInstanceCount(), is(1L));
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult().size(), is(1));

    assertThat(
      (String) result.getResult().get(0).getInputVariables().get(inputVariableIdToFilterOn).getValue(),
      startsWith("2019-01-01T01:00:00")
    );
  }

  private HashMap<String, InputVariableEntry> createInputs(final double amountValue,
                                                           final String invoiceDateTime) {
    return new HashMap<String, InputVariableEntry>() {{
      put(INPUT_AMOUNT_ID, new InputVariableEntry(INPUT_AMOUNT_ID, "Invoice Amount", "Double", amountValue));
      put(
        INPUT_CATEGORY_ID,
        new InputVariableEntry(INPUT_CATEGORY_ID, "Invoice Category", "String", "Misc")
      );
      put(
        INPUT_INVOICE_DATE_ID,
        new InputVariableEntry(INPUT_INVOICE_DATE_ID, "Invoice Date", "Date", invoiceDateTime)
      );
    }};
  }

  private void startDecisionInstanceWithInputVars(final String id,
                                                  final HashMap<String, InputVariableEntry> inputVariables) {
    engineRule.startDecisionInstance(
      id,
      inputVariables.entrySet().stream().collect(toMap(
        entry -> getInputVariableNameForId(entry.getKey()),
        entry -> entry.getValue().getValue()
      ))
    );
  }

  private static String getInputVariableNameForId(String inputId) {
    switch (inputId) {
      case INPUT_AMOUNT_ID:
        return INPUT_VARIABLE_AMOUNT;
      case INPUT_CATEGORY_ID:
        return INPUT_VARIABLE_INVOICE_CATEGORY;
      case INPUT_INVOICE_DATE_ID:
        return INPUT_VARIABLE_INVOICE_DATE;
      default:
        throw new IllegalStateException("Unsupported inputVariableId: " + inputId);
    }
  }

  private RawDataDecisionReportResultDto evaluateReport(DecisionReportDataDto reportData) {
    Response response = evaluateReportAndReturnResponse(reportData);
    assertThat(response.getStatus(), is(200));

    return response.readEntity(RawDataDecisionReportResultDto.class);
  }

  private Response evaluateReportAndReturnResponse(DecisionReportDataDto reportData) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      .execute();
  }

}
