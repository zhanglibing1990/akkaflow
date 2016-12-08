package com.kent.workflow

import scala.xml.XML
import com.kent.workflow.node.NodeInfo
import java.util.Date
import com.kent.pub.DeepCloneable
import com.kent.util.Util
import java.util.Calendar
import com.kent.workflow.node.NodeInfo
import com.kent.pub.Daoable
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException

class WorkflowInfo(var name:String) extends DeepCloneable[WorkflowInfo] with Daoable[WorkflowInfo] {
	import com.kent.workflow.WorkflowInfo.WStatus._
  var id: String = _
  var desc: String = _
  var nodeList:List[NodeInfo] = List()
  var createTime: Date = _
  var params:Map[String, String] = Map()

  def deepClone(): WorkflowInfo = {
	  val wf = new WorkflowInfo(name)
	  this.deepCloneAssist(wf)
	  wf
	}

  def deepCloneAssist(wf: WorkflowInfo): WorkflowInfo = {
	  wf.id = id
	  wf.params = this.params.map(x => (x._1,x._2)).toMap
	  wf.nodeList = this.nodeList.map { _.deepClone() }.toList
	  wf.createTime = if(createTime == null) null else new Date(this.createTime.getTime)
	  wf
	}

  def delete(implicit conn: Connection): Boolean = {
    var result = false
    try{
      conn.setAutoCommit(false)
	    result = executeSql(s"delete from workflow where id='${id}'")
	    executeSql(s"delete from node where workflow_id='${id}'")
	    conn.commit()
    }catch{
      case e: SQLException => e.printStackTrace();conn.rollback()
    }
    result
  }

  def getEntity(implicit conn: Connection): Option[WorkflowInfo] = {
    val queryStr = """
         select id,name,description,create_time,last_update_time
         from workflow where id='"""+id+"""'
                    """
    querySql(queryStr, (rs: ResultSet) =>{
          if(rs.next()){
            this.desc = rs.getString("description")
            this.name= rs.getString("name")
            this
          }else{
            null
          }
      })
	}

  def save(implicit conn: Connection): Boolean = {
    var result = false;
    import com.kent.util.Util._
    try{
      conn.setAutoCommit(false)
  	  val insertSql = s"""
  	     insert into workflow values(${withQuate(id)},${withQuate(name)},${withQuate(desc)},
  	     ${withQuate(formatStandarTime(createTime))},${withQuate(formatStandarTime(nowDate))})
  	    """
  	  val updateSql = s"""
  	    update workflow set name = ${withQuate(name)}, 
  	                        description = ${withQuate(desc)}, 
  	                        last_update_time = ${withQuate(formatStandarTime(nowDate))}
  	    where id = '${id}'
  	    """
  	  if(this.getEntity.isEmpty){
      	result = executeSql(insertSql)     
      }else{
        result = executeSql(updateSql)
      }
  	  executeSql(s"delete from node where workflow_id='${id}'")
  	  this.nodeList.foreach { _.save }
  	  conn.commit()
    }catch{
      case e: SQLException => e.printStackTrace(); conn.rollback()
    }
	  result
	}
}

object WorkflowInfo {
  def apply(content: String): WorkflowInfo = WorkflowInfo(XML.loadString(content))
  def apply(node: scala.xml.Node): WorkflowInfo = parseXmlNode(node)
  /**
   * 解析xml为一个对象
   */
  def parseXmlNode(node: scala.xml.Node): WorkflowInfo = {
      val nameOpt = node.attribute("name")
      val idOpt = node.attribute("id")
      val descOpt = node.attribute("desc")
      val createTimeOpt = node.attribute("create-time")
      if(nameOpt == None) throw new Exception("节点<work-flow/>未配置name属性")
      val id = if(idOpt == None) Util.produce6UUID else idOpt.get.text
      val wf = new WorkflowInfo(nameOpt.get.text)
      wf.id = id
      if(descOpt != None) wf.desc = descOpt.get.text
    	wf.nodeList = (node \ "_").map{x => val n = NodeInfo(x); n.workflowId = id; n }.toList
    	if(createTimeOpt != None)
    	  wf.createTime = Util.getStandarTimeWithStr(createTimeOpt.get.text) 	
    	else
    	  wf.createTime = Util.nowDate
    	wf
  }
  
  object WStatus extends Enumeration {
    type WStatus = Value
    val W_PREP, W_RUNNING, W_SUSPENDED, W_SUCCESSED, W_FAILED, W_KILLED = Value
    def getWstatusWithId(id: Int): WStatus = {
      var sta: WStatus = W_PREP  
      WStatus.values.foreach { x => if(x.id == id) return x }
      sta
    }
    
  }
}