package io.github.cloudiator.examples.internal;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kairosdb.client.HttpClient;
import org.kairosdb.client.builder.*;
import org.kairosdb.client.response.Queries;
import org.kairosdb.client.response.QueryResponse;
import org.kairosdb.client.response.Results;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by Frank on 24.11.2016.
 */
public class KairosDbConnection {
    private static final int PULL_DELAY = 5000;
    //TODO maybe not needed anymore, since now visor immediately pushes values
    private static final int MAX_RETRY = 2;
    private String url;
    private String ip;
    private Integer port;

    public static final Logger LOGGER = LogManager.getLogger(KairosDbConnection.class);

    public KairosDbConnection(String ip, Integer port) {
        this.ip = ip;
        this.port = port;
        this.url = "http://" + this.ip + ":" + this.port.toString();
    }

    synchronized public void write(String metricName, List<Tag> tags, double value) {
        long timeStamp = System.currentTimeMillis();
        write(metricName, tags, value, timeStamp);
    }

    synchronized public void write(String metricName, List<Tag> tags, double value,
        long timeStamp) {

        HttpClient httpClient = null;
        try {
            httpClient = new HttpClient(this.url);
        } catch (MalformedURLException e) {
            LOGGER.error("URL is malformed: " + this.url);
            return;
        }

        MetricBuilder metricBuilder = MetricBuilder.getInstance();

        org.kairosdb.client.builder.Metric kairosMetric = metricBuilder.addMetric(metricName)
            .addDataPoint(timeStamp - PULL_DELAY /* TODO testing purpose */, value);
        //we need to add the tags
        for (Tag t : tags) {
            kairosMetric.addTag(t.getName(), t.getValue());
        }

        try {
            httpClient.pushMetrics(metricBuilder);
        } catch (URISyntaxException e) {
            LOGGER.error("URL Syntax is malformed: " + this.url, e);
            return;
        } catch (IOException e) {
            LOGGER.error("Something went wrong on pushing metrics: " + this.url, e);
            return;
        }
    }

    synchronized public List<Double> getAggregatedValue(String metricName, List<Tag> tags,
        int time) {

        Date now = new Date();
        now.setTime(now.getTime());

        HttpClient httpClient = null;
        try {
            httpClient = new HttpClient(this.url);
        } catch (MalformedURLException e) {
            LOGGER.error("HTTP client problem: " + this.url, e);
            return new ArrayList<Double>();
        }

        QueryBuilder builder = QueryBuilder.getInstance();

        builder = builder.setStart(time, TimeUnit.SECONDS);

        QueryMetric queryMetric = builder.addMetric(metricName);

        for (Tag tag : tags) {
            queryMetric = queryMetric.addTag(tag.getName(), tag.getValue());
        }

        QueryResponse response = null;
        try {
            response = httpClient.query(builder);
        } catch (URISyntaxException e) {
            LOGGER.error("URI has wrong syntax: " + e.getMessage());
            return new ArrayList<Double>();
        } catch (IOException e) {
            LOGGER.error("Something went wrong on querying: " + e.getMessage());
            return new ArrayList<Double>();
        }

        List<Queries> queries = response.getQueries();

        if (queries.size() > 1) {
            LOGGER.error("queries too much");
            throw new RuntimeException("more than one query in response");
        }
        if (queries.isEmpty()) {
            LOGGER.error("query is empty");
            throw new RuntimeException("no query in response");
        }

        List<Results> results = queries.get(0).getResults();

        if (results.size() > 1) {
            LOGGER.error("results too much");
            throw new RuntimeException("too much results received");
        }
        if (results.isEmpty()) {
            LOGGER.error("results empty");
        }

        List<Double> result = new ArrayList<Double>();

        for (DataPoint point : results.get(0).getDataPoints()) {
            try {
                result.add(point.doubleValue());
            } catch (DataFormatException e) {
                LOGGER.error(
                    "Value could not be transformed to value: " + metricName + "; on: " + point
                        .toString());
            }
        }

        if (result.isEmpty()) {
            LOGGER.debug("Empty result for query: ");
        }

        return result;
    }
}
