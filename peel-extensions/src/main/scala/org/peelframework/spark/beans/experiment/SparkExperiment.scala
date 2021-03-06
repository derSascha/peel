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
package org.peelframework.spark.beans.experiment

import java.io.FileWriter
import java.nio.file._

import com.typesafe.config.Config
import org.peelframework.core.beans.data.{DataSet, ExperimentOutput}
import org.peelframework.core.beans.experiment.Experiment
import org.peelframework.core.beans.system.System
import org.peelframework.core.util.shell
import org.peelframework.spark.beans.system.Spark
import spray.json._

/** An `Expriment` implementation which handles the execution of a single Spark job. */
class SparkExperiment(
    command: String,
    systems: Set[System],
    runner : Spark,
    runs   : Int,
    inputs : Set[DataSet],
    outputs: Set[ExperimentOutput],
    name   : String,
    config : Config) extends Experiment(command, systems, runner, runs, inputs, outputs, name, config) {

  def this(
    command: String,
    runner : Spark,
    runs   : Int,
    inputs : Set[DataSet],
    outputs: Set[ExperimentOutput],
    name   : String,
    config : Config) = this(command, Set.empty[System], runner, runs, inputs, outputs, name, config)

  override def run(id: Int, force: Boolean): Experiment.Run[Spark] = {
    new SparkExperiment.SingleJobRun(id, this, force)
  }

  def copy(name: String = name, config: Config = config) = {
    new SparkExperiment(command, systems, runner, runs, inputs, outputs, name, config)
  }
}

object SparkExperiment {

  case class State(
    command         : String,
    runnerID        : String,
    runnerName      : String,
    runnerVersion   : String,
    var runExitCode : Option[Int] = None,
    var runTime     : Long = 0) extends Experiment.RunState

  object StateProtocol extends DefaultJsonProtocol with NullOptions {
    implicit val stateFormat = jsonFormat6(State)
  }

  /**
   * A private inner class encapsulating the logic of single run.
   */
  class SingleJobRun(val id: Int, val exp: SparkExperiment, val force: Boolean) extends Experiment.SingleJobRun[Spark, State] {

    import org.peelframework.spark.beans.experiment.SparkExperiment.StateProtocol._

    val runnerLogPath = exp.config.getString("system.spark.path.log")

    override def isSuccessful = state.runExitCode.getOrElse(-1) == 0

    override protected def loadState(): State = {
      if (Files.isRegularFile(Paths.get(s"$home/state.json"))) {
        try {
          scala.io.Source.fromFile(s"$home/state.json").mkString.parseJson.convertTo[State]
        } catch {
          case e: Throwable => State(command, exp.runner.beanName, exp.runner.name, exp.runner.version)
        }
      } else {
        State(command, exp.runner.beanName, exp.runner.name, exp.runner.version)
      }
    }

    override protected def writeState() = {
      val fw = new FileWriter(s"$home/state.json")
      fw.write(state.toJson.prettyPrint)
      fw.close()
    }

    override protected def runJob() = {
      // try to execute the experiment run plan
      val (runExit, t) = Experiment.time(this !(command, s"$home/run.out", s"$home/run.err"))
      state.runTime = t
      state.runExitCode = Some(runExit)
    }

    override def cancelJob() = {

    }

    private def !(command: String, outFile: String, errFile: String) = {
      shell ! s"${exp.config.getString("system.spark.path.home")}/bin/spark-submit ${command.trim} > $outFile 2> $errFile"
    }
  }

}

