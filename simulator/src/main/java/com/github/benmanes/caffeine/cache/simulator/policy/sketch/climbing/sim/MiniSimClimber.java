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
package com.github.benmanes.caffeine.cache.simulator.policy.sketch.climbing.sim;

import java.util.List;

import com.github.benmanes.caffeine.cache.simulator.BasicSettings;
import com.github.benmanes.caffeine.cache.simulator.policy.sketch.WindowTinyLfuPolicy;
import com.github.benmanes.caffeine.cache.simulator.policy.sketch.WindowTinyLfuPolicy.WindowTinyLfuSettings;
import com.github.benmanes.caffeine.cache.simulator.policy.sketch.climbing.HillClimber;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.google.errorprone.annotations.Var;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * A MinSim version for W-TinyLFU.
 * <p>
 * The algorithm is explained by the authors in
 * <a href="https://www.usenix.org/system/files/conference/atc17/atc17-waldspurger.pdf">Cache
 * Modeling and Optimization using Miniature Simulation</a>.
 *
 * @author ohadey@gmail.com (Ohad Eytan)
 */
@SuppressWarnings("PMD.SuspiciousConstantFieldName")
public final class MiniSimClimber implements HillClimber {
  private static final HashFunction hasher = Hashing.murmur3_32_fixed(0x7f3a2142);

  private final WindowTinyLfuPolicy[] minis;
  private final long[] prevMisses;
  private final int samplingRate;
  private final int cacheSize;
  private final int period;

  private int sample;
  private double prevPercent;

  public MiniSimClimber(Config config) {
    var settings = new MiniSimSettings(config);
    this.cacheSize = Math.toIntExact(settings.maximumSize());
    samplingRate = (cacheSize / 1000) > 100 ? 1000 : (cacheSize / 100);
    var simulationSettings = new WindowTinyLfuSettings(ConfigFactory
        .parseString("maximum-size = " + (cacheSize / samplingRate))
        .withFallback(config));
    this.prevPercent = 1 - settings.percentMain().get(0);
    this.period = settings.minisimPeriod();
    this.minis = new WindowTinyLfuPolicy[101];
    this.prevMisses = new long[minis.length];

    for (int i = 0; i < minis.length; i++) {
      double percentMain = 1.0 - (i / 100.0);
      minis[i] = new WindowTinyLfuPolicy(percentMain, simulationSettings);
    }
  }

  @Override
  public void onHit(long key, QueueType queue, boolean isFull) {
    onAccess(key);
  }

  @Override
  public void onMiss(long key, boolean isFull) {
    onAccess(key);
  }

  private void onAccess(long key) {
    sample++;

    if (Math.floorMod(hasher.hashLong(key).asInt(), samplingRate) < 1) {
      for (WindowTinyLfuPolicy policy : minis) {
        policy.record(key);
      }
    }
  }

  @Override
  public Adaptation adapt(double windowSize,
      double probationSize, double protectedSize, boolean isFull) {
    if (sample <= period) {
      return Adaptation.hold();
    }

    long[] periodMisses = new long[minis.length];
    for (int i = 0; i < minis.length; i++) {
      periodMisses[i] = minis[i].stats().missCount() - prevMisses[i];
      prevMisses[i] = minis[i].stats().missCount();
    }
    @Var int minIndex = 0;
    for (int i = 1; i < periodMisses.length; i++) {
      if (periodMisses[i] < periodMisses[minIndex]) {
        minIndex = i;
      }
    }

    sample = 0;
    double oldPercent = prevPercent;
    double newPercent = minIndex < 80 ? minIndex / 100.0 : 0.8;
    prevPercent = newPercent;
    return (newPercent > oldPercent)
        ? Adaptation.increaseWindow((int) ((newPercent - oldPercent) * cacheSize))
        : Adaptation.decreaseWindow((int) ((oldPercent - newPercent) * cacheSize));
  }

  static final class MiniSimSettings extends BasicSettings {
    public MiniSimSettings(Config config) {
      super(config);
    }
    public List<Double> percentMain() {
      return config().getDoubleList("hill-climber-window-tiny-lfu.percent-main");
    }
    public int minisimPeriod() {
      return config().getInt("hill-climber-window-tiny-lfu.minisim.period");
    }
  }
}
