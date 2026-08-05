/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gbif.dp.service.http;

import java.time.Duration;
import java.util.Objects;

public record RetryConfig(
  int retries,
  double multiplier,
  Duration initialDelay) {

  public RetryConfig {
    if (retries < 0) {
      throw new IllegalArgumentException("retries must be >= 0");
    }
    if (!Double.isFinite(multiplier) || multiplier < 1.0) {
      throw new IllegalArgumentException("multiplier must be finite and >= 1.0");
    }
    Objects.requireNonNull(initialDelay, "initialDelay");
    if (initialDelay.isNegative()) {
      throw new IllegalArgumentException("initialDelay must be >= 0");
    }
  }

  public Duration delayForRetry(int retryIndex) {
    if (retryIndex < 0) {
      throw new IllegalArgumentException("retryIndex must be >= 0");
    }

    double delayMillis =
      initialDelay.toMillis() * Math.pow(multiplier, retryIndex);

    return Duration.ofMillis(
      delayMillis >= Long.MAX_VALUE
        ? Long.MAX_VALUE
        : Math.round(delayMillis));
  }
}
