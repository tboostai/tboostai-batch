package com.tboostai_batch.config;

import com.tboostai_batch.component.ProductDetailsComponent;
import com.tboostai_batch.component.ProductsSearchComponent;
import com.tboostai_batch.component.TokenRequestComponent;
import com.tboostai_batch.component.chunk_components.EbayProductFetchAndProcessItemProcessor;
import com.tboostai_batch.component.chunk_components.EbayProductFetchAndProcessItemReader;
import com.tboostai_batch.component.chunk_components.EbayProductFetchAndProcessItemWriter;
import com.tboostai_batch.entity.ebay.dto.EbayRespBasicDTO;
import com.tboostai_batch.entity.inner_model.EbayCompleteInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.JobRegistry;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;

@Configuration
@EnableBatchProcessing(dataSourceRef = "batchDataSource", transactionManagerRef = "batchTransactionManager")
public class EbayBatchJobConfig {

    private static final Logger logger = LoggerFactory.getLogger(EbayBatchJobConfig.class);

    // Spring Batch Initialization Beans
    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;

    // Steps Components
    private final TokenRequestComponent tokenRequestComponent;
    private final ProductsSearchComponent productsSearchComponent;
    private final ProductDetailsComponent productDetailsComponent;

    private final EbayProductFetchAndProcessItemReader ebayProductFetchAndProcessItemReader;
    private final EbayProductFetchAndProcessItemProcessor ebayProductFetchAndProcessItemProcessor;
    private final EbayProductFetchAndProcessItemWriter ebayProductFetchAndProcessItemWriter;

    @Autowired
    public EbayBatchJobConfig(JobRepository jobRepository,
                              PlatformTransactionManager transactionManager,
                              TokenRequestComponent tokenRequestComponent,
                              ProductsSearchComponent productsSearchComponent,
                              ProductDetailsComponent productDetailsComponent,
                              EbayProductFetchAndProcessItemReader ebayProductFetchAndProcessItemReader,
                              EbayProductFetchAndProcessItemProcessor ebayProductFetchAndProcessItemProcessor,
                              EbayProductFetchAndProcessItemWriter ebayProductFetchAndProcessItemWriter){
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.tokenRequestComponent = tokenRequestComponent;
        this.productsSearchComponent = productsSearchComponent;
        this.productDetailsComponent = productDetailsComponent;
        this.ebayProductFetchAndProcessItemReader = ebayProductFetchAndProcessItemReader;
        this.ebayProductFetchAndProcessItemProcessor = ebayProductFetchAndProcessItemProcessor;
        this.ebayProductFetchAndProcessItemWriter = ebayProductFetchAndProcessItemWriter;
    }

    @Bean(name = "ebayRetrieveVehicleDataBatchJob")
    public Job ebayBatchJob(Step requestTokenStep, Step searchProductsStep, Step searchProductDetailsStep, Step readAndProcessProductDataChunkStep) {
        logger.info("Start building ebay batch job");
        JobBuilder jobBuilder = new JobBuilder("ebayRetrieveVehicleDataBatchJob", jobRepository);
        return jobBuilder.start(requestTokenStep)
                .next(searchProductsStep)
                .next(searchProductDetailsStep)
                .next(readAndProcessProductDataChunkStep)
                .preventRestart()
                .build();
    }

    // Step 1: 请求 Access Token
    @Bean
    public Step requestTokenStep() {
        StepBuilder stepBuilder = new StepBuilder("requestTokenStep", jobRepository);
        return stepBuilder.tasklet((contribution, chunkContext) -> {
            logger.info("Executing Step 1: Requesting Access Token");
            String token = tokenRequestComponent.requestAccessToken();
            logger.info("Token requested from step 1 is {}, will be saved in step context", token);
            // 将 Access Token 存入 JobExecutionContext
            chunkContext.getStepContext().getStepExecution().getJobExecution()
                    .getExecutionContext().put("accessToken", token);
            return RepeatStatus.FINISHED;
        }, transactionManager).build();
    }

    // Step 2: Search items by category id
    @Bean
    public Step searchProductsStep() {
        StepBuilder stepBuilder = new StepBuilder("searchProductsStep", jobRepository);
        return stepBuilder.tasklet((contribution, chunkContext) -> {
            logger.info("Executing Step 2: Searching Products");

            // 从 JobExecutionContext 中读取 Access Token
            String token = (String) chunkContext.getStepContext().getStepExecution()
                    .getJobExecution().getExecutionContext().get("accessToken");
            logger.info("Using Access Token: {}", token);

            // 假设查询条件并搜索产品
            List<String> productIds = productsSearchComponent.searchProducts(token);

            logger.info("Requested products: {}, \n Found {} products",productIds, productIds.size());
            return RepeatStatus.FINISHED;
        }, transactionManager).build();
    }

    // Step 3: Search item details by item id
    @Bean
    public Step searchProductDetailsStep() {
        StepBuilder stepBuilder = new StepBuilder("searchProductDetailsStep", jobRepository);
        return stepBuilder.tasklet((contribution, chunkContext) -> {
            logger.info("Executing Step 3: Searching Product Details");
            // 从 JobExecutionContext 中读取 Access Token
            String token = (String) chunkContext.getStepContext().getStepExecution()
                    .getJobExecution().getExecutionContext().get("accessToken");
            List<EbayRespBasicDTO> ebayRespBasicDTOList = productDetailsComponent.getProductDetails(token);
            logger.info("There are {} items and the first item is :{}", ebayRespBasicDTOList.size(), ebayRespBasicDTOList.get(0));

            return RepeatStatus.FINISHED;
        }, transactionManager).build();
    }

    // Step 4-5: Process product data in chunks
    @Bean
    public Step readAndProcessProductDataChunkStep() {
        StepBuilder stepBuilder = new StepBuilder("readAndProcessProductDataChunkStep", jobRepository);
        logger.info("Executing Step 4: Read Product Data Chunk, Process Data Chunk and Save to DB");
        return stepBuilder.<List<EbayRespBasicDTO>, List<EbayCompleteInfo>>chunk(1, transactionManager)  // 一次处理整个 List<String>，chunk 大小设为 1
                .reader(ebayProductFetchAndProcessItemReader)  // Step 3: 读取产品 ID 列表
                .processor(ebayProductFetchAndProcessItemProcessor)  // Step 4: 获取产品详情并处理数据
                .writer(ebayProductFetchAndProcessItemWriter)  // Step 5: 保存数据到数据库
                .transactionManager(transactionManager)  // 事务管理
                .build();
    }
}
