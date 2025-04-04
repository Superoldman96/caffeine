/*
 * Copyright 2018 Ohad Eytan. All Rights Reserved.
 *
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
package com.github.benmanes.caffeine.cache.simulator.policy.sketch;

import java.util.Arrays;
import java.util.stream.IntStream;

import org.apache.commons.math3.stat.regression.SimpleRegression;

import com.clearspring.analytics.stream.StreamSummary;
import com.github.benmanes.caffeine.cache.simulator.BasicSettings;
import com.github.benmanes.caffeine.cache.simulator.admission.countmin4.PeriodicResetCountMin4;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * An indicator for the recency vs. frequency bias.
 *
 * @author ohadey@gmail.com (Ohad Eytan)
 */
public final class Indicator {
  private final int k;
  private final int ssSize;
  private final Hinter hinter;
  private final EstSkew estSkew;
  private final PeriodicResetCountMin4 sketch;

  private long sample;

  public Indicator(Config config) {
    var settings = new IndicatorSettings(config);
    this.sketch = new PeriodicResetCountMin4(
        ConfigFactory.parseString("maximum-size = 5000").withFallback(config));
    this.ssSize = settings.ssSize();
    this.estSkew = new EstSkew();
    this.hinter = new Hinter();
    this.k = settings.k();
  }

  public void record(long key) {
    int hint = sketch.frequency(key);
    hinter.increment(hint);
    sketch.increment(key);
    estSkew.record(key);
    sample++;
  }

  public void reset() {
    hinter.reset();
    estSkew.reset();
    sample = 0;
  }

  public long getSample() {
    return sample;
  }

  public int[] getFreqs() {
    return hinter.freq;
  }

  public double getSkew() {
    return estSkew.estSkew(k);
  }

  public double getHint() {
    return hinter.getAverage();
  }

  public double getIndicator() {
    double skew = getSkew();
    return (getHint() * (skew < 1 ? 1 - Math.pow(skew, 3) : 0)) / 15.0;
  }

  private static final class Hinter {
    final int[] freq = new int[16];

    int sum;
    int count;

    public void increment(int i) {
      sum += i;
      count++;
      freq[i]++;
    }

    public void reset() {
      sum = count = 0;
      Arrays.fill(freq, 0);
    }

    public double getAverage() {
      return sum / (double) count;
    }
  }

  private final class EstSkew {
    StreamSummary<Long> stream;

    public EstSkew() {
      stream = new StreamSummary<>(ssSize);
    }

    public void record(long key) {
      stream.offer(key);
    }

    public void reset() {
      stream = new StreamSummary<>(ssSize);
    }

    public IntStream getTopK(int k) {
      return stream.topK(k).stream().mapToInt(counter -> (int) counter.getCount());
    }

    public double estSkew(int k) {
      int[] idx = { 1 };
      var regression = new SimpleRegression();
      getTopK(k).forEachOrdered(freq -> regression.addData(Math.log(idx[0]++), Math.log(freq)));
      return -regression.getSlope();
    }
  }

  static final class IndicatorSettings extends BasicSettings {
    public IndicatorSettings(Config config) {
      super(config);
    }
    public int k() {
      return config().getInt("indicator.k");
    }
    public int ssSize() {
      return config().getInt("indicator.ss-size");
    }
  }
}
