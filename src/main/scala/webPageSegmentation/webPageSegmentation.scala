package webPageSegmentation
import org.apache.spark.lineage.LineageContext
import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.lineage.LineageContext._


object webPageSegmentation {
  def main(args: Array[String]) {
    val sparkConf = new SparkConf().setMaster(if (args.length > 2) args(2) else "local[*]")
    sparkConf.setAppName("webPageSegmentation")
    val ctx = SparkContext.getOrCreate(sparkConf)
    ctx.setLogLevel("ERROR")

    val before_data = "src/resources/webPageSegmentation/before"
    val after_data = "src/resources/webPageSegmentation/after"

    val lc = new LineageContext(ctx)

    val before = lc.textFile(before_data).map(_.split(','))
    val after = ctx.textFile(after_data).map(_.split(','))

    val boxes_before = before
      .filter(r => r(5).toInt == 0)
      .map(r => (r(0) + "*" + r(r.length - 2) + "*" + r.last, (r(0), r.slice(1, r.length - 2).map(_.toInt).toVector)))
    val boxes_after = after.map(r => (r(0) + "*" + r(r.length - 2) + "*" + r.last, (r(0), r.slice(1, r.length - 2).map(_.toInt).toVector)))
    val boxes_after_by_site = after.map(r => (r(0), (r.slice(1, r.length - 2).map(_.toInt).toVector, r(r.length - 2), r.last)))
      .groupByKey()

    val pairs = boxes_before.join(boxes_after)

    val changed = pairs
      .filter {
        case (_, ((_, v1), (_, v2))) => !v1.equals(v2)
      }
      .map {
        case (k, (_, (url, a))) =>
          val Array(_, cid, ctype) = k.split('*')
          (url, (a, cid, ctype))
      }

//        println("-------")
    val inter = changed.join(boxes_after_by_site)
    inter.map {
      case (url, ((box1, _, _), lst)) => (url, lst.map { case (box, _, _) => box }.map(intersects(_, box1)))
    }

//      .collect().foreach(println)

    boxes_before.collectWithId.foreach(println)

    lc.setCaptureLineage(false)

    //data lineage
    var linRdd = boxes_before.getLineage()

    //track all wrong input
    linRdd = linRdd.goBackAll()
    println("This is lineage of this wrong input")
    linRdd.show(true)

    //    val iRects =  pairs.map{ case (id, (rect1, rect2)) => (id, intersects(rect1, rect2))}
    //    iRects.collect().foreach(println)

    ctx.stop()
  }

  def intersects(rect1: IndexedSeq[Int],
                 rect2: IndexedSeq[Int]): Option[(Int, Int, Int, Int)] = {

    val IndexedSeq(aSWx, aSWy, aHeight, aWidth) = rect1
    val IndexedSeq(bSWx, bSWy, bHeight, bWidth) = rect2
    val endpointax = aSWx + aWidth;
    val startpointax = aSWx;
    val endpointay = aSWy + aHeight;
    val startpointay = aSWy;
    val endpointbx = bSWx + bWidth;
    val startpointbx = bSWx;
    val endpointby = bSWy + bHeight;
    val startpointby = bSWy;


    if ((endpointax < startpointbx) && (startpointax < startpointbx)) {
      return None;
    }
    if ((endpointbx < startpointax) && (startpointbx < startpointax)) {
      return None;
    }

    if ((endpointby < startpointay) && (startpointby < startpointay)) {
      return None;
    }

    if ((endpointay < startpointby) && (startpointay < startpointby)) {
      return None;
    }

    if (startpointay > endpointby) {
      return None;
    }

    var iSWx, iSWy, iWidth, iHeight = 0

    if ((startpointax <= startpointbx) && (endpointbx <= endpointax)) {
      iSWx = startpointbx;
      iSWy = if (startpointay < startpointby) startpointby else startpointay;
      iWidth = bWidth;
      val top = if (endpointby < endpointay) endpointby else endpointay;
      iHeight = (top - iSWy);
    }
    else if ((startpointbx <= startpointax) && (endpointax <= endpointbx)) {
      iSWx = startpointax;
      iSWy = if (startpointay < startpointby) startpointby else startpointay;
      iWidth = aWidth;
      val top = if (endpointby < endpointay) endpointby else endpointay;
      iHeight = (top - iSWy);
    }
    else if ((startpointax >= startpointbx) && (startpointax <= endpointbx)) {
      iSWx = startpointax;
      iSWy = if (startpointay > startpointby) startpointay else startpointby;
      iWidth = (endpointbx - startpointax);
      val top = if (endpointby < endpointay) endpointby else endpointay;
      iHeight = (top - iSWy);
    }
    else if ((startpointbx >= startpointax) && (startpointbx <= endpointax)) {
      iSWx = startpointbx;
      iSWy = if (startpointay > startpointby) startpointay else startpointby;
      iWidth = (endpointax - startpointbx);
      val top = if (endpointby < endpointay) endpointby else endpointay;
      iHeight = (top - iSWy);
    }
    //    else if (startpointbx > startpointax && startpointbx < endpointax && endpointby <= startpointay){
    //      iSWx  = startpointbx;
    //      iSWy = startpointay;
    //      iWidth = bWidth;
    //      iHeight = 0;
    //    }
    //    else if (startpointax > startpointbx && startpointax < endpointbx && endpointay <= startpointby) {
    //      iSWx  = startpointax;
    //      iSWy = endpointby;
    //      iWidth = aWidth;
    //      iHeight = 0;
    //    }

    Some(iSWx, iSWy, iHeight, iWidth)
  }

}
