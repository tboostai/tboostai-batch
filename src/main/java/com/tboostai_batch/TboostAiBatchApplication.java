package com.tboostai_batch;

import com.tboostai_batch.service.scheduled.EbayRetrieveVehicleDataBatchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.batch.BatchAutoConfiguration;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = BatchAutoConfiguration.class)
@EnableScheduling
public class TboostAiBatchApplication {

    private final EbayRetrieveVehicleDataBatchService batchService;

    @Autowired
    public TboostAiBatchApplication(EbayRetrieveVehicleDataBatchService batchService) {
        this.batchService = batchService;
    }

    public static void main(String[] args) {
        SpringApplication.run(TboostAiBatchApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void runJobAfterStartup() {
        batchService.runManualJob();
    }

}
