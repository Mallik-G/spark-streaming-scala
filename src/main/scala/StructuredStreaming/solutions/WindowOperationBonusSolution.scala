package StructuredStreaming.solutions

import java.sql.Timestamp

import org.apache.log4j.{Level, Logger}
import org.apache.spark.sql.{Dataset, SparkSession}
import org.apache.spark.sql.functions.{count, max, window}

object WindowOperationBonusSolution {
	def main(args: Array[String]): Unit = {
		val spark = SparkSession
			.builder
			.appName("WindowOperationsBonusSolution")
			.master("local[*]")
			.getOrCreate()

		import spark.implicits._

		Logger.getRootLogger.setLevel(Level.ERROR)

		spark.read.json("tweetFiles").printSchema()

		val schema = spark.read
			.format("csv")
			.option("header", value = false)
			.option("inferSchema", value = true)
			.load("tweetFiles")
			.schema

		val records = spark.readStream
			.schema(schema)
			.format("csv")
			.option("header", value = false)
			.load("tweetFiles")

		case class TweetData(id: BigInt, userName: String, place: String, replyToScreenName: String,
		                     createdAt: String, textLength: BigInt, firstHashtag: String)

		implicit val tweetDataEncoder = org.apache.spark.sql.Encoders.kryo[TweetData]

		val query = records.
			as[(BigInt, String, String, String, String, BigInt, String)].
			map(r => TweetData(r._1, r._2, r._3, r._4, r._5, r._6, r._7)).
			map(t => (t.replyToScreenName, new Timestamp(t.createdAt.toLong), t.id,
						if (t.firstHashtag == null) 0 else t.firstHashtag.length)).
			toDF("replyToScreenName", "createdAt", "id", "hashtagLength").
			withWatermark("createdAt", "3 minutes").
			groupBy(window($"createdAt", "10 minutes", "30 seconds"), $"replyToScreenName").
			agg(count("id").as("count"), count("replyToScreenName").as("replied"), max("hashtagLength")).
			select($"count", $"replies" / $"count" as "RepliesPercentage", $"max(hashtagLength)").
			writeStream.format("console").
			queryName("exerciseOutput").start

		query.awaitTermination()
	}

}
