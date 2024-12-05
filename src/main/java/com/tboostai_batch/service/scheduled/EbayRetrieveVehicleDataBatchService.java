package com.tboostai_batch.service.scheduled;

import com.tboostai_batch.service.RedisService;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.*;
import org.springframework.batch.core.configuration.JobRegistry;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class EbayRetrieveVehicleDataBatchService implements JobExecutionListener {

    private static final Logger logger = LoggerFactory.getLogger(EbayRetrieveVehicleDataBatchService.class);

    private final JobLauncher jobLauncher;
    private final JobRegistry jobRegistry;
    private final RedisService redisService;
    private final PlatformTransactionManager platformTransactionManager;

    @Autowired
    public EbayRetrieveVehicleDataBatchService(JobLauncher jobLauncher,
                                               JobRegistry jobRegistry,
                                               RedisService redisService,
                                               @Qualifier("batchTransactionManager") PlatformTransactionManager platformTransactionManager) {
        this.jobLauncher = jobLauncher;
        this.jobRegistry = jobRegistry;
        this.redisService = redisService;
        this.platformTransactionManager = platformTransactionManager;
    }

    // Uncomment for scheduled job in production
    // @Scheduled(cron = "0 0 */2 * * *")
    public void scheduledRunJob() {
        logger.info("Scheduled run job");
        runJobWithConfiguredJobName();
    }

    // Manual method for local testing
    public void runManualJob() {
        logger.info("Starting manual run of Job.");
        runJobWithConfiguredJobName();
    }

    private void runJobWithConfiguredJobName() {
        String jobName = System.getenv("BATCH_JOB_NAME"); // 从环境变量中读取 Job 名称
        logger.info("EbayRetrieveVehicleDataBatchService - Job name: {}", jobName);
        if (jobName == null || jobName.isEmpty()) {
            logger.error("Job name not provided. Please set the BATCH_JOB_NAME environment variable.");
            return;
        }

        try {
            Job job = jobRegistry.getJob(jobName); // 从 JobRegistry 获取指定的 job
            JobParameters params = new JobParametersBuilder()
                    .addLong("time", System.currentTimeMillis()) // 使用当前时间戳避免重复执行相同 job
                    .toJobParameters();

            JobExecution jobExecution = jobLauncher.run(job, params); // 执行指定的 job
            logger.info("Job Execution Status: {}", jobExecution.getStatus());
        } catch (Exception e) {
            logger.error("Failed to execute job with name '{}': {}", jobName, e.getMessage(), e);
        }
    }

    @Override
    public void beforeJob(@NotNull JobExecution jobExecution) {
        // Code to execute before the job starts (optional)
    }

    @Override
    public void afterJob(@NotNull JobExecution jobExecution) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(platformTransactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transactionTemplate.execute(status -> {
            logger.info("Batch job finished, status of the job is {}", jobExecution.getStatus());

            if (jobExecution.getStatus().isUnsuccessful() || jobExecution.getStatus() == BatchStatus.FAILED) {
                logger.info("Batch job failed");
            }

            if (jobExecution.getStatus() == BatchStatus.COMPLETED) {
                logger.info("Batch job completed");
            }

            logger.info("Start clear Redis template cache.");
            redisService.clearAllTempData();
            return null;
        });
    }
}
