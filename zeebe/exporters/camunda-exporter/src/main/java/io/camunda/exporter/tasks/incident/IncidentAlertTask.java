/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.incident;

import io.camunda.exporter.tasks.BackgroundTask;
import io.camunda.exporter.tasks.alerts.AlertDefinitionRepository;
import io.camunda.exporter.tasks.incident.IncidentUpdateRepository.IncidentDocument;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.webapps.schema.entities.AlertDefinitionEntity.Filter;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.time.Instant;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class IncidentAlertTask implements BackgroundTask {

  private final IncidentUpdateRepository incidentUpdateRepository;
  private final AlertDefinitionRepository alertDefinitionRepository;
  private final ConnectConfiguration connectConfiguration;

  public IncidentAlertTask(
      final IncidentUpdateRepository incidentUpdateRepository,
      final AlertDefinitionRepository alertDefinitionRepository,
      final ConnectConfiguration connectConfiguration) {
    this.incidentUpdateRepository = incidentUpdateRepository;
    this.alertDefinitionRepository = alertDefinitionRepository;
    this.connectConfiguration = connectConfiguration;
  }

  @Override
  public CompletionStage<Integer> execute() {
    try {
      System.out.println("Incident alert task is executing");
      final Map<String, IncidentDocument> incidents =
          incidentUpdateRepository
              .getIncidentDocumentsBefore(Instant.now().toEpochMilli())
              .toCompletableFuture()
              .join();

      final var alertDefinitions = alertDefinitionRepository.getAll();
      incidents.forEach(
          (id, incident) -> {
            System.out.println("berkay Incident: " + id + " - " + incident);
            final String errorMessage = incident.incident().getErrorMessage();
            alertDefinitions.stream()
                .filter(
                    alert ->
                        alert.getFilters().stream()
                            .map(Filter::processDefinitionKey)
                            .anyMatch(
                                pdk ->
                                    pdk.equals(
                                        incident.incident().getProcessDefinitionKey().toString())))
                .forEach(
                    alert -> {
                      final String message =
                          errorMessage
                              + " \n \n"
                              + "See incident in Operate for more details: "
                              + "http://localhost:8080" // TODO - it needs to be added to the config
                              // as such
                              // .connectConfiguration.getOperateFrontendUrl()
                              + "/operate/processes/"
                              + incident.incident().getProcessInstanceKey();
                      sendEmail(alert.getChannel().value(), message);
                    });
          });
      return CompletableFuture.completedFuture(1);
    } catch (final Exception e) {
      return CompletableFuture.failedFuture(e);
    }
  }

  private void sendEmail(final String to, final String incidentMessage) {
    final String from = "camunda.alerts@gmail.com";
    final String host = "smtp.gmail.com";

    final Properties properties = System.getProperties();
    properties.put("mail.smtp.host", host);
    properties.put("mail.smtp.port", "465");
    properties.put("mail.smtp.ssl.enable", "true");
    properties.put("mail.smtp.auth", "true");

    final Session session =
        Session.getInstance(
            properties,
            new Authenticator() {
              @Override
              protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(
                    "camunda.alerts@gmail.com", "qqmx qpiy llml uegn");
              }
            });

    try {
      final MimeMessage message = new MimeMessage(session);
      message.setFrom(new InternetAddress(from));
      message.addRecipient(Message.RecipientType.TO, new InternetAddress(to));
      message.setSubject("Camunda Alert");
      message.setText(incidentMessage);

      Transport.send(message);
    } catch (final MessagingException mex) {
      mex.printStackTrace();
    }
  }
}
