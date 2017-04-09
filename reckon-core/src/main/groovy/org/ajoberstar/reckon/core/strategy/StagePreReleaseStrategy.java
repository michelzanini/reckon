/*
 * Copyright 2015-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ajoberstar.reckon.core.strategy;

import com.github.zafarkhaja.semver.Version;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.ajoberstar.reckon.core.PreReleaseStrategy;
import org.ajoberstar.reckon.core.VcsInventory;
import org.ajoberstar.reckon.core.Versions;

public final class StagePreReleaseStrategy implements PreReleaseStrategy {
  public static final String FINAL_STAGE = "final";
  private static final Pattern STAGE_REGEX = Pattern.compile("^(?<name>\\w+)(?:\\.(?<num>\\d+))?");

  private final Set<String> stages;
  private final String defaultStage;
  private final Supplier<Optional<String>> stageSupplier;

  public StagePreReleaseStrategy(Set<String> stages, Supplier<Optional<String>> stageSupplier) {
    this.stages = stages;
    this.defaultStage =
        stages
            .stream()
            .sorted()
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("No stages provided."));
    this.stageSupplier = stageSupplier;
  }

  @Override
  public Version reckonTargetVersion(VcsInventory inventory, Version targetNormal) {
    String stage = stageSupplier.get().orElse(null);

    if (stage != null && !stages.contains(stage)) {
      String message = String.format("Stage \"%s\" is not one of: %s", stage, stages);
      throw new IllegalArgumentException(message);
    }

    if (FINAL_STAGE.equals(stage)) {
      return targetNormal;
    }

    Version targetBase =
        targetNormal.equals(Versions.getNormal(inventory.getBaseVersion()))
            ? inventory.getBaseVersion()
            : targetNormal;

    String baseStageName;
    int baseStageNum;
    Matcher matcher = STAGE_REGEX.matcher(targetBase.getPreReleaseVersion());
    if (matcher.find()) {
      baseStageName = matcher.group("name");
      baseStageNum = Optional.ofNullable(matcher.group("num")).map(Integer::parseInt).orElse(-1);
    } else {
      baseStageName = defaultStage;
      baseStageNum = 0;
    }

    if (stage == null) {
      return targetBase
          .setPreReleaseVersion(
              baseStageName + "." + baseStageNum + "." + inventory.getCommitsSinceBase())
          .setBuildMetadata(inventory.getCommitId());
    } else if (stage.equals(baseStageName)) {
      int num = baseStageNum > 0 ? baseStageNum + 1 : 1;
      return targetBase.setPreReleaseVersion(stage + "." + num);
    } else {
      return targetBase.setPreReleaseVersion(stage + ".1");
    }
  }
}
