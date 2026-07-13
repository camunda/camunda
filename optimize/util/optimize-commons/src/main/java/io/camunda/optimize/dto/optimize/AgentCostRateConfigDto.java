/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The user-configured LLM cost rates, stored as a single document in the agent-cost-rate index. The
 * whole config is replaced on every save (the UI sends the full rate table). Rates are consumed at
 * import time to stamp {@code agentTotalCost} on process instances (see the agent loop in {@code
 * ZeebeProcessInstanceScriptFactory}); {@code effectiveFrom} is stored for display only and does
 * not drive the forward-only cost calculation.
 */
public class AgentCostRateConfigDto {

  private String currency = "USD";
  private String unit = "per_1k_tokens";
  private List<AgentCostRateDto> rates = new ArrayList<>();

  public AgentCostRateConfigDto() {}

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(final String currency) {
    this.currency = currency;
  }

  public String getUnit() {
    return unit;
  }

  public void setUnit(final String unit) {
    this.unit = unit;
  }

  public List<AgentCostRateDto> getRates() {
    return rates != null ? rates : List.of();
  }

  public void setRates(final List<AgentCostRateDto> rates) {
    this.rates = rates;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final AgentCostRateConfigDto that = (AgentCostRateConfigDto) o;
    return Objects.equals(currency, that.currency)
        && Objects.equals(unit, that.unit)
        && Objects.equals(rates, that.rates);
  }

  @Override
  public int hashCode() {
    return Objects.hash(currency, unit, rates);
  }

  @Override
  public String toString() {
    return "AgentCostRateConfigDto(currency="
        + currency
        + ", unit="
        + unit
        + ", rates="
        + rates
        + ")";
  }

  public enum Fields {
    currency,
    unit,
    rates
  }

  /** A single per-model, per-direction price expressed per 1000 tokens. */
  public static class AgentCostRateDto {

    private String model;
    private Double inputRatePer1k;
    private Double outputRatePer1k;
    private String effectiveFrom;

    public AgentCostRateDto() {}

    public String getModel() {
      return model;
    }

    public void setModel(final String model) {
      this.model = model;
    }

    public Double getInputRatePer1k() {
      return inputRatePer1k;
    }

    public void setInputRatePer1k(final Double inputRatePer1k) {
      this.inputRatePer1k = inputRatePer1k;
    }

    public Double getOutputRatePer1k() {
      return outputRatePer1k;
    }

    public void setOutputRatePer1k(final Double outputRatePer1k) {
      this.outputRatePer1k = outputRatePer1k;
    }

    public String getEffectiveFrom() {
      return effectiveFrom;
    }

    public void setEffectiveFrom(final String effectiveFrom) {
      this.effectiveFrom = effectiveFrom;
    }

    @Override
    public boolean equals(final Object o) {
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final AgentCostRateDto that = (AgentCostRateDto) o;
      return Objects.equals(model, that.model)
          && Objects.equals(inputRatePer1k, that.inputRatePer1k)
          && Objects.equals(outputRatePer1k, that.outputRatePer1k)
          && Objects.equals(effectiveFrom, that.effectiveFrom);
    }

    @Override
    public int hashCode() {
      return Objects.hash(model, inputRatePer1k, outputRatePer1k, effectiveFrom);
    }

    @Override
    public String toString() {
      return "AgentCostRateDto(model="
          + model
          + ", inputRatePer1k="
          + inputRatePer1k
          + ", outputRatePer1k="
          + outputRatePer1k
          + ", effectiveFrom="
          + effectiveFrom
          + ")";
    }

    public enum Fields {
      model,
      inputRatePer1k,
      outputRatePer1k,
      effectiveFrom
    }
  }
}
