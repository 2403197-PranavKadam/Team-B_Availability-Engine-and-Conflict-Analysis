package com.teamb.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamb.scheduler.api.AvailabilityServer;
import com.teamb.scheduler.api.ApiKeyManager;
import java.nio.file.Path;
import com.teamb.scheduler.db.SlotRepository;
import com.teamb.scheduler.index.IntervalIndex;
import com.teamb.scheduler.index.VersionRegistry;
import com.teamb.scheduler.search.SearchRanking;
import com.teamb.scheduler.validator.RequestValidator;
import com.teamb.scheduler.conflict.ConflictAnalytics;
import com.teamb.scheduler.result.ResultBuilder;

public class Main {
    public static void main(String[] args) throws Exception {
        SlotRepository repository = new SlotRepository();
        repository.initSchema();
        repository.seedMockData();

        var index = new IntervalIndex(repository.findAppointments());
        var apiKeys = new ApiKeyManager(Path.of("data", "api-key.txt"));
        var server = new AvailabilityServer(
                8080,
                new RequestValidator(),
                index,
                new SearchRanking(),
                new ConflictAnalytics(),
                new ResultBuilder(),
                new VersionRegistry(),
                new ObjectMapper(),
                repository,
                apiKeys
        );

        server.start();
        System.out.println("Team B Availability Engine running at http://localhost:8080");
        System.out.println("Web form: http://localhost:8080");
        System.out.println("Generated/reused API key (keep private): " + apiKeys.getApiKey());
        System.out.println("API key file: data/api-key.txt");
        System.out.println("Use the web form to enter provider, date, and duration dynamically.");
        System.out.println("Press Ctrl+C to stop.");
    }
}
