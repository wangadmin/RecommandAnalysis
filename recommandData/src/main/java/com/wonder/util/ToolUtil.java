package com.wonder.util;

import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.logging.log4j.Logger;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.json.JSONException;
import org.json.JSONObject;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Tuple;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.Set;

/**
 * Created by Administrator on 2017/9/12.
 */
public class ToolUtil {

    private static final Logger log = org.apache.logging.log4j.LogManager.getLogger(ToolUtil.class.getName());
    private static String dbName = ResourcesManager.getProp("mongodb.db");
    private static String collName = ResourcesManager.getProp("mongodb.coll");
    private static MongoCollection<Document> coll = MongoDBUtil.instance.getCollection(dbName, collName);
    private static CloseableHttpClient client = HttpClients.createDefault();
    private static String GetScoreUrl = ResourcesManager.getProp("getscore.url");

    private static Jedis jedis = RedisUtil.getJedis();

    /**
     * 根据key向db中写入数据
     *
     * @param key 用户id
     */
    public static void redisInsert2Db(String key) {
        log.info("从redis同步数据到mongodb");
        Set<Tuple> getKeySet = jedis.zrevrangeWithScores(key, 0, -1);
        Document docInsert = new Document();
        for (Tuple t : getKeySet) {
            String itemName = t.getElement();
            double itemScore = t.getScore();
            //此时写入数据库
            docInsert.put(itemName, itemScore);
        }
        docInsert.put("userid", key);
        coll.insertOne(docInsert);
    }

    /**
     * 根据userid删除db中数据
     *
     * @param useridKey
     */
    public static void deleteDb(String useridKey, String useridValue) {
        BasicDBObject doc = new BasicDBObject();
        doc.put(useridKey, useridValue);
        coll.deleteOne(doc);
    }


    /**
     * 根据userid更新数据,没有则新增
     *
     * @param
     * @param newdoc
     * @return
     */
    public static void updateById(String useridKey, String useridValue, Document newdoc) {
        Bson filter = Filters.eq(useridKey, useridValue);
        coll.updateOne(filter, new Document("$set", newdoc));
    }


    /**
     * 根据key来查询doc
     *
     * @param key
     * @return
     */
    public static Document getDbDoc(String key) {
        Document doc = null;
        BasicDBObject searchQuery = new BasicDBObject();
        searchQuery.put("userid", key);
        doc = coll.find(searchQuery).first();
        return doc;
    }


    /**
     * 同步db数据到redis
     *
     * @param document
     * @param key
     */
    public static void dbInsert2Redis(Document document, String key) {
        try {
            JSONObject jsonObj = new JSONObject(document.toJson().toString());
            Iterator it = jsonObj.keys();
            while (it.hasNext()) {
                String keyJson = (String) it.next();
                String valueJson = jsonObj.getString(key);
                if (!"_id".equalsIgnoreCase(keyJson)) {
                    jedis.zadd(key, Double.valueOf(valueJson), keyJson);
                }
            }
        } catch (JSONException e) {
            log.error(e.getStackTrace());
        }
    }


    /**
     * 得到系统当前时间
     */
    public static String currentTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
        Calendar calendar = Calendar.getInstance();//日历对象
        calendar.setTime(new Date());//设置当前日期
        return sdf.format(calendar.getTime());
    }


    /**
     * 计算日期的相隔天数
     *
     * @throws ParseException
     */
    public static long getTimelag(String startTime, String endTime) throws ParseException {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMdd");
        Date beginDate = simpleDateFormat.parse(startTime);
        Date endDate = simpleDateFormat.parse(endTime);
        long days = (endDate.getTime() - beginDate.getTime()) / (24 * 60 * 60 * 1000);
        return days;
    }

    /**
     * 根据key从redis中得到推荐栏目
     *
     * @param key
     * @return
     */
    public static String getRecommandItem(String key) {
        String getRecommandItem = null;
        Set sets = jedis.zrevrangeByScore(key, "+inf", "-inf", 0, 2);
        Iterator<String> itSets = sets.iterator();
        while (itSets.hasNext()) {
            String firstItem = itSets.next();
            if (!"time".equalsIgnoreCase(firstItem)) {    //用来判断成员是否是time
                getRecommandItem = firstItem;
                break;
            }
        }
        return getRecommandItem;
    }


    /**
     * 根据sctionType得到操作分数
     * @param actionType
     * @return
     * @throws IOException
     * @throws JSONException
     */
    public static String getScore(String actionType) throws IOException, JSONException {

        HttpPost httpPost = new HttpPost(
                GetScoreUrl+actionType);
        httpPost.setHeader("Accept", "application/json");
        httpPost.setHeader("Content-type", "application/json");
        CloseableHttpResponse response = client.execute(httpPost);
        BufferedReader br = new BufferedReader(new InputStreamReader(
                response.getEntity().getContent()));
        String line = null;
        StringBuilder sb = new StringBuilder();
        while ((line = br.readLine()) != null) {
            sb.append(line);
        }
        String scores = null;
        JSONObject jsonObj = new JSONObject(sb.toString());
        Iterator it = jsonObj.keys();
        while (it.hasNext()) {
            if("score".equalsIgnoreCase((String) it.next())){
                scores = (String) jsonObj.get("score");
            }
        }
        return scores;
    }

}
