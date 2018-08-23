package pasa.bigdata.nju.smartfd.inductor

import java.util
import java.util.concurrent.{Callable, Executors, Future}

import pasa.bigdata.nju.smartfd.structures.{FDList, FDTree}

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.ExecutionException

/**
  * Pruning-and-generation using the multi-threaded technique
  */
class MultiThreadInductor extends AbstractInductor {
  private var posCover: FDTree = null

  def this(posCover: FDTree){
    this
    this.posCover = posCover
  }

  override def updatePositiveCover(nonFds: FDList, currentAttribute: Int): Int = {//@tested
    val executors = Executors.newFixedThreadPool(posCover.numAttributes)
    val futures = new ArrayBuffer[Future[Int]]()
    for(i <- currentAttribute until posCover.numAttributes){
      futures += executors.submit(new UpdataPositiveCoverTask(nonFds, i))
    }

    var removedFD = 0
    try{
      for(i <- 0 until futures.size){
        removedFD += futures(i).get()
      }
    }catch {
      case ex: InterruptedException => ex.printStackTrace()
      case ex: ExecutionException => ex.printStackTrace()
    }finally{
      executors.shutdown()
    }

    while(!executors.isTerminated){}
    removedFD
  }

  protected def specializePositiveCover(lhs: util.BitSet,
                                        rhs: Int,
                                        nonFds: FDList,
                                        currentAttribute: Int): util.ArrayList[util.BitSet] = {
    val numAttributes: Int = this.posCover.getChildren.length
    val specLhss: util.ArrayList[util.BitSet] = this.posCover.getAllFds(lhs, rhs, currentAttribute)
    var i = 0
    while(i < specLhss.size()){
      val specLhs = specLhss.get(i)
      if(specLhs.get(currentAttribute)){
        this.posCover.removeFunctionalDependency(specLhs, rhs)
        var attr = numAttributes - 1
        while(attr >= currentAttribute){
          if (!lhs.get(attr) && (attr != rhs)) {
            specLhs.set(attr)
            val currentAttr = specLhs.nextSetBit(0)
            if(this.posCover.getChildren == null || this.posCover.getChildren(currentAttr) == null){
              if(!this.posCover.isFd(rhs)){
                this.posCover.addFunctionalDependency(specLhs.get(0, numAttributes), rhs)
              }
            }else if(!this.posCover.getChildren(currentAttr).containsFdOrGeneralization(specLhs.get(0, numAttributes), rhs, specLhs.nextSetBit(currentAttr + 1))){
              this.posCover.addFunctionalDependency(specLhs.get(0, numAttributes), rhs)
            }
            specLhs.clear(attr)
          }
          attr -= 1
        }
      }
      i += 1
    }
    specLhss
  }

  private class UpdataPositiveCoverTask(val nonFds: FDList, val currentAttribute: Int) extends Callable[Int] {
    override def call(): Int = {
      val invalidLhs = new util.HashSet[util.BitSet]()
      var i = nonFds.getFdLevels.size - 1
      while(i >= 0){
        val nonFdLevel: util.ArrayList[util.BitSet] = nonFds.getFdLevels.get(i)
        var j = 0
        while(j < nonFdLevel.size()){
          val lhs = nonFdLevel.get(j)
          if(lhs.get(currentAttribute)){
            val fullRhs: util.BitSet = lhs.get(0, posCover.getNumAttributes)
            fullRhs.flip(0, posCover.getNumAttributes)
            var rhs: Int = fullRhs.nextSetBit(0)
            while (rhs >= 0 && rhs < posCover.getNumAttributes ) {
              invalidLhs.addAll(specializePositiveCover(lhs, rhs, nonFds, currentAttribute))
              rhs = fullRhs.nextSetBit(rhs + 1)
            }
          }
          j += 1
        }
        i -= 1
      }
      invalidLhs.size()
    }
  }
}
