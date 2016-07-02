package com.olacabs.fabric.executor.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.olacabs.fabric.common.util.MesosDnsResolver;
import com.olacabs.fabric.executor.MetadataSource;
import com.olacabs.fabric.model.computation.ComputationSpec;
import com.spotify.dns.LookupResult;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.util.List;

/**
 * Created by santanu.s on 22/09/15.
 */
public class HttpMetadataSource implements MetadataSource {
    private static final Logger logger = LoggerFactory.getLogger(HttpMetadataSource.class);
    private HttpClient httpClient = HttpClients.createDefault();
    private final ObjectMapper mapper;
    private MesosDnsResolver dnsResolver;

    public HttpMetadataSource(ObjectMapper mapper, MesosDnsResolver dnsResolver) {
        this.mapper = mapper;
        this.dnsResolver = dnsResolver;
    }

    @Override
    public ComputationSpec load(String url) throws Exception {
        //First resolve to a real host-port combo if it's Mesos DNS
        String specEndpoint = url;
        String specHost = specEndpoint.split("//")[1].split("/")[0];

        if (specHost.endsWith(".")) {
            // Need to do a srv record resolution
            List<LookupResult> results = dnsResolver.dnsSrvLookup(specHost);

            if (!results.isEmpty()) {
                specHost = InetAddress.getByName(results.get(0).host()).getHostAddress() + ":" + results.get(0).port();
                logger.info("Setting spec host to: " + specHost);
            } else {
                throw new RuntimeException(String.format("Dns Srv Resolution for spec host failed: %s", specHost));
            }
            specEndpoint = specEndpoint.split("//")[0] + "//" + specHost + "/" + specEndpoint.split("//")[1].replaceFirst(".*\\./", "");
        }
        HttpGet getRequest = new HttpGet(specEndpoint);
        HttpResponse response = httpClient.execute(getRequest);
        if(HttpStatus.SC_OK != response.getStatusLine().getStatusCode()) {
            throw new Exception("Received: " + response.getStatusLine().getStatusCode() + " from spec url: " + url);
        }

        return mapper.readValue(EntityUtils.toByteArray(response.getEntity()), ComputationSpec.class);
    }
}