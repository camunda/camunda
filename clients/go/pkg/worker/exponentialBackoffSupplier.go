// Copyright © 2018 Camunda Services GmbH (info@camunda.com)
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

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

func NewExponentialBackoffBuilder() ExponentialBackoff {
	return ExponentialBackoff{
		maxDelay:      time.Second * 5,
		minDelay:      time.Millisecond * 50,
		backoffFactor: 1.6,
		jitterFactor:  0.1,
		random:        rand.New(rand.NewSource(time.Now().Unix())), //nolint G404, we dont need a secure random number generator
	}
}

func (e ExponentialBackoff) MaxDelay(maxDelay time.Duration) ExponentialBackoffBuilder {
	e.maxDelay = maxDelay
	return e
}

func (e ExponentialBackoff) MinDelay(minDelay time.Duration) ExponentialBackoffBuilder {
	e.minDelay = minDelay
	return e
}

func (e ExponentialBackoff) BackoffFactor(backoffFactor float64) ExponentialBackoffBuilder {
	e.backoffFactor = backoffFactor
	return e
}

func (e ExponentialBackoff) JitterFactor(jitterFactor float64) ExponentialBackoffBuilder {
	e.jitterFactor = jitterFactor
	return e
}

func (e ExponentialBackoff) Random(random *rand.Rand) ExponentialBackoffBuilder {
	e.random = random
	return e
}

func (e ExponentialBackoff) Build() BackoffSupplier {
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
