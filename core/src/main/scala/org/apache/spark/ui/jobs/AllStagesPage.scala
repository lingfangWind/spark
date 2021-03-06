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

package org.apache.spark.ui.jobs

import javax.servlet.http.HttpServletRequest

import scala.xml.{Node, NodeSeq}

import org.apache.spark.scheduler.Schedulable
import org.apache.spark.status.PoolData
import org.apache.spark.status.api.v1._
import org.apache.spark.ui.{UIUtils, WebUIPage}

/** Page showing list of all ongoing and recently finished stages and pools */
private[ui] class AllStagesPage(parent: StagesTab) extends WebUIPage("") {
  private val sc = parent.sc
  private def isFairScheduler = parent.isFairScheduler

  def render(request: HttpServletRequest): Seq[Node] = {
    val allStages = parent.store.stageList(null)

    val activeStages = allStages.filter(_.status == StageStatus.ACTIVE)
    val pendingStages = allStages.filter(_.status == StageStatus.PENDING)
    val completedStages = allStages.filter(_.status == StageStatus.COMPLETE)
    val failedStages = allStages.filter(_.status == StageStatus.FAILED).reverse

    val numCompletedStages = completedStages.size
    val numFailedStages = failedStages.size
    val subPath = "stages"

    val activeStagesTable =
      new StageTableBase(parent.store, request, activeStages, "active", "activeStage",
        parent.basePath, subPath, parent.isFairScheduler, parent.killEnabled, false)
    val pendingStagesTable =
      new StageTableBase(parent.store, request, pendingStages, "pending", "pendingStage",
        parent.basePath, subPath, parent.isFairScheduler, false, false)
    val completedStagesTable =
      new StageTableBase(parent.store, request, completedStages, "completed", "completedStage",
        parent.basePath, subPath, parent.isFairScheduler, false, false)
    val failedStagesTable =
      new StageTableBase(parent.store, request, failedStages, "failed", "failedStage",
        parent.basePath, subPath, parent.isFairScheduler, false, true)

    // For now, pool information is only accessible in live UIs
    val pools = sc.map(_.getAllPools).getOrElse(Seq.empty[Schedulable]).map { pool =>
      val uiPool = parent.store.asOption(parent.store.pool(pool.name)).getOrElse(
        new PoolData(pool.name, Set()))
      pool -> uiPool
    }.toMap
    val poolTable = new PoolTable(pools, parent)

    val shouldShowActiveStages = activeStages.nonEmpty
    val shouldShowPendingStages = pendingStages.nonEmpty
    val shouldShowCompletedStages = completedStages.nonEmpty
    val shouldShowFailedStages = failedStages.nonEmpty

    val completedStageNumStr = if (numCompletedStages == completedStages.size) {
      s"$numCompletedStages"
    } else {
      s"$numCompletedStages, only showing ${completedStages.size}"
    }

    val summary: NodeSeq =
      <div>
        <ul class="unstyled">
          {
            if (shouldShowActiveStages) {
              <li>
                <a href="#active"><strong>Active Stages:</strong></a>
                {activeStages.size}
              </li>
            }
          }
          {
            if (shouldShowPendingStages) {
              <li>
                <a href="#pending"><strong>Pending Stages:</strong></a>
                {pendingStages.size}
              </li>
            }
          }
          {
            if (shouldShowCompletedStages) {
              <li id="completed-summary">
                <a href="#completed"><strong>Completed Stages:</strong></a>
                {completedStageNumStr}
              </li>
            }
          }
          {
            if (shouldShowFailedStages) {
              <li>
                <a href="#failed"><strong>Failed Stages:</strong></a>
                {numFailedStages}
              </li>
            }
          }
        </ul>
      </div>

    var content = summary ++
      {
        if (sc.isDefined && isFairScheduler) {
          <h4>Fair Scheduler Pools ({pools.size})</h4> ++ poolTable.toNodeSeq
        } else {
          Seq.empty[Node]
        }
      }
    if (shouldShowActiveStages) {
      content ++= <h4 id="active">Active Stages ({activeStages.size})</h4> ++
      activeStagesTable.toNodeSeq
    }
    if (shouldShowPendingStages) {
      content ++= <h4 id="pending">Pending Stages ({pendingStages.size})</h4> ++
      pendingStagesTable.toNodeSeq
    }
    if (shouldShowCompletedStages) {
      content ++= <h4 id="completed">Completed Stages ({completedStageNumStr})</h4> ++
      completedStagesTable.toNodeSeq
    }
    if (shouldShowFailedStages) {
      content ++= <h4 id ="failed">Failed Stages ({numFailedStages})</h4> ++
      failedStagesTable.toNodeSeq
    }
    UIUtils.headerSparkPage("Stages for All Jobs", content, parent)
  }
}
