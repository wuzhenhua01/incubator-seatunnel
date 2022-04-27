/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.seatunnel.spark.transform

import java.security.SecureRandom
import java.util.UUID

import scala.collection.JavaConversions._

import com.google.common.annotations.VisibleForTesting
import org.apache.commons.math3.random.{RandomGenerator, Well19937c}
import org.apache.seatunnel.common.Constants
import org.apache.seatunnel.common.config.CheckConfigUtil.checkAllExists
import org.apache.seatunnel.common.config.CheckResult
import org.apache.seatunnel.shade.com.typesafe.config.ConfigFactory
import org.apache.seatunnel.spark.{BaseSparkTransform, SparkEnvironment}
import org.apache.spark.sql.{Dataset, Row}
import org.apache.spark.sql.expressions.UserDefinedFunction
import org.apache.spark.sql.functions.{col, udf}

class Uuid extends BaseSparkTransform {
  private var prng: RandomGenerator = _

  override def process(df: Dataset[Row], env: SparkEnvironment): Dataset[Row] = {
    val key = config.getString("fields")

    val func: UserDefinedFunction = udf(() => {
      generate(config.getString("prefix"))
    })
    var filterDf = df.withColumn(Constants.ROW_TMP, func())
    filterDf = filterDf.withColumn(key, col(Constants.ROW_TMP))
    val ds = filterDf.drop(Constants.ROW_TMP)
    if (func != null) {
      env.getSparkSession.udf.register("Uuid", func)
    }
    ds
  }

  override def checkConfig(): CheckResult = {
    checkAllExists(config, "fields")
  }

  override def prepare(env: SparkEnvironment): Unit = {
    val defaultConfig = ConfigFactory.parseMap(Map("prefix" -> "", "secure" -> false))
    config = config.withFallback(defaultConfig)
    if (config.getBoolean("secure")) {
      val rand = new SecureRandom
      val seed = for (_ <- 0 until 728) yield rand.nextInt
      prng = new Well19937c(seed.toArray)
    }
  }

  @VisibleForTesting
  def generate(prefix: String): String = {
    val uuid = if (prng == null) UUID.randomUUID else new UUID(prng.nextLong, prng.nextLong)
    prefix + uuid
  }

  @VisibleForTesting
  def setPrng(prng: RandomGenerator) {
    this.prng = prng
  }
}
