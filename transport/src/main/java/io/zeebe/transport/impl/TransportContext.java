/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.transport.impl;

import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.dispatcher.FragmentHandler;
import io.zeebe.transport.ClientOutput;
import io.zeebe.transport.EndpointRegistry;
import io.zeebe.transport.ServerOutput;
import java.time.Duration;

public class TransportContext {
  private String name;
  private int messageMaxLength;
  private Duration channelKeepAlivePeriod;

  private ServerOutput serverOutput;
  private ClientOutput clientOutput;

  private Dispatcher receiveBuffer;
  private Dispatcher sendBuffer;

  private RemoteAddressListImpl remoteAddressList;
  private EndpointRegistry endpointRegistry;

  private FragmentHandler receiveHandler;

  private ServerSocketBinding serverSocketBinding;

  private TransportChannelFactory channelFactory;

  public int getMessageMaxLength() {
    return messageMaxLength;
  }

  public void setMessageMaxLength(int messageMaxLength) {
    this.messageMaxLength = messageMaxLength;
  }

  public ServerOutput getServerOutput() {
    return serverOutput;
  }

  public void setServerOutput(ServerOutput serverOutput) {
    this.serverOutput = serverOutput;
  }

  public ClientOutput getClientOutput() {
    return clientOutput;
  }

  public void setClientOutput(ClientOutput clientOutput) {
    this.clientOutput = clientOutput;
  }

  public Dispatcher getReceiveBuffer() {
    return receiveBuffer;
  }

  public void setReceiveBuffer(Dispatcher receiveBuffer) {
    this.receiveBuffer = receiveBuffer;
  }

  public RemoteAddressListImpl getRemoteAddressList() {
    return remoteAddressList;
  }

  public void setRemoteAddressList(RemoteAddressListImpl remoteAddressList) {
    this.remoteAddressList = remoteAddressList;
  }

  public EndpointRegistry getEndpointRegistry() {
    return endpointRegistry;
  }

  public void setEndpointRegistry(EndpointRegistry endpointRegistry) {
    this.endpointRegistry = endpointRegistry;
  }

  public FragmentHandler getReceiveHandler() {
    return receiveHandler;
  }

  public void setReceiveHandler(FragmentHandler receiveHandler) {
    this.receiveHandler = receiveHandler;
  }

  public ServerSocketBinding getServerSocketBinding() {
    return serverSocketBinding;
  }

  public void setServerSocketBinding(ServerSocketBinding serverSocketBinding) {
    this.serverSocketBinding = serverSocketBinding;
  }

  public Duration getChannelKeepAlivePeriod() {
    return channelKeepAlivePeriod;
  }

  public void setChannelKeepAlivePeriod(Duration channelKeepAlivePeriod) {
    this.channelKeepAlivePeriod = channelKeepAlivePeriod;
  }

  public TransportChannelFactory getChannelFactory() {
    return channelFactory;
  }

  public void setChannelFactory(TransportChannelFactory channelFactory) {
    this.channelFactory = channelFactory;
  }

  public void setSendBuffer(Dispatcher sendBuffer) {
    this.sendBuffer = sendBuffer;
  }

  public Dispatcher getSetSendBuffer() {
    return sendBuffer;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }
}
