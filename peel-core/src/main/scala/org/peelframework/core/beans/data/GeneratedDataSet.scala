/**
 * Copyright (C) 2014 TU Berlin (peel@dima.tu-berlin.de)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.peelframework.core.beans.data

import org.peelframework.core.beans.system.{FileSystem, Job, System}

/** Dataset that does not physically exist yet and is generated by a separate [[org.peelframework.core.beans.system.Job  Job]].
  *
  * The data is generated and materialized by a separate [[org.peelframework.core.beans.system.Job  Job]] which is responsible
  * for generating the data and materializing it at the specified location.
  *
  * If the data already exists at the specified location, it is '''not''' generated again!
  *
  * @param src A reference to the job that generates the dataset.
  * @param dst The path where the data is stored. The job has to make sure that the data is actually stored at that location.
  * @param fs Filesystem that is used to check if this dataset already exists.
  */
class GeneratedDataSet(val src: Job[System], val dst: String, val fs: System with FileSystem) extends DataSet(dst, Set[System](fs, src.runner)) {

  import scala.language.implicitConversions

  override def materialize() = {
    // resolve parameters from the current config in dst
    val dst = resolve(this.dst)

    logger.info(s"Generating data for target '$dst'")
    try {
      src.execute()
    } catch {
      case e: Throwable => throw new RuntimeException(s"Could not generate data for target '$dst' [$e]", e)
    }
  }
}