/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package worker

import (
	"math"
	"math/rand"
	"time"
)

type ExponentialBackoffBuilder interface {
	MaxDelay(time.Duration) ExponentialBackoffBuilder
	MinDelay(time.Duration) ExponentialBackoffBuilder
	BackoffFactor(float64) ExponentialBackoffBuilder
	JitterFactor(float64) ExponentialBackoffBuilder
	Random(*rand.Rand) ExponentialBackoffBuilder
	Build() BackoffSupplier
}

type ExponentialBackoffBuilderImpl struct {
	minDelay, maxDelay          time.Duration
	backoffFactor, jitterFactor float64
	random                      *rand.Rand
}

func NewExponentialBackoffBuilder() ExponentialBackoffBuilderImpl {
	return ExponentialBackoffBuilderImpl{
		maxDelay:      time.Second * 5,
		minDelay:      time.Millisecond * 50,
		backoffFactor: 1.6,
		jitterFactor:  0.1,
		random:        rand.New(rand.NewSource(time.Now().Unix())),
	}
}

func (e ExponentialBackoffBuilderImpl) MaxDelay(maxDelay time.Duration) ExponentialBackoffBuilder {
	e.maxDelay = maxDelay
	return e
}

func (e ExponentialBackoffBuilderImpl) MinDelay(minDelay time.Duration) ExponentialBackoffBuilder {
	e.minDelay = minDelay
	return e
}

func (e ExponentialBackoffBuilderImpl) BackoffFactor(backoffFactor float64) ExponentialBackoffBuilder {
	e.backoffFactor = backoffFactor
	return e
}

func (e ExponentialBackoffBuilderImpl) JitterFactor(jitterFactor float64) ExponentialBackoffBuilder {
	e.jitterFactor = jitterFactor
	return e
}

func (e ExponentialBackoffBuilderImpl) Random(random *rand.Rand) ExponentialBackoffBuilder {
	e.random = random
	return e
}

func (e ExponentialBackoffBuilderImpl) Build() BackoffSupplier {
	return ExponentialBackoff{
		minDelay:      e.minDelay,
		maxDelay:      e.maxDelay,
		backoffFactor: e.backoffFactor,
		jitterFactor:  e.jitterFactor,
		random:        e.random,
	}
}

type ExponentialBackoff struct {
	minDelay, maxDelay          time.Duration
	backoffFactor, jitterFactor float64
	random                      *rand.Rand
}

func (e ExponentialBackoff) SupplyRetryDelay(currentRetryDelay time.Duration) time.Duration {
	y := float64(currentRetryDelay.Milliseconds()) * e.backoffFactor
	delay := math.Max(math.Min(float64(e.maxDelay.Milliseconds()), y), float64(e.minDelay.Milliseconds()))
	jitter := e.computeJitter(delay)
	retryDelay := math.Round(delay + jitter)
	return time.Duration(retryDelay * float64(time.Millisecond))
}

func (e ExponentialBackoff) computeJitter(value float64) float64 {
	minFactor := value * -e.jitterFactor
	maxFactor := value * e.jitterFactor

	return (e.random.Float64() * (maxFactor - minFactor)) + minFactor
}
