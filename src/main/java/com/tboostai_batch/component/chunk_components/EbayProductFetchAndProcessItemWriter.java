package com.tboostai_batch.component.chunk_components;

import com.tboostai_batch.config.MapperManager;
import com.tboostai_batch.config.RepoManager;
import com.tboostai_batch.entity.db_model.*;
import com.tboostai_batch.entity.inner_model.EbayCompleteInfo;
import com.tboostai_batch.mapper.ebay.*;
import com.tboostai_batch.repo.*;
import com.tboostai_batch.service.RedisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.annotation.BeforeStep;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.tboostai_batch.common.GeneralConstants.EBAY_PLATFORM;

@Component
public class EbayProductFetchAndProcessItemWriter implements ItemWriter<List<EbayCompleteInfo>> {

    private static final Logger logger = LoggerFactory.getLogger(EbayProductFetchAndProcessItemWriter.class);
    private final RedisService redisService;
    private final MapperManager mapperManager;
    private final RepoManager repoManager;
    private StepExecution stepExecution;

    @BeforeStep
    public void setStepExecution(StepExecution stepExecution) {
        this.stepExecution = stepExecution;
    }

    public EbayProductFetchAndProcessItemWriter(RedisService redisService, MapperManager mapperManager, RepoManager repoManager) {
        this.redisService = redisService;
        this.mapperManager = mapperManager;
        this.repoManager = repoManager;
    }

    @Override
    public void write(Chunk<? extends List<EbayCompleteInfo>> chunk) {
        logger.info("Write Product Data Chunk");
        List<? extends List<EbayCompleteInfo>> ebayCompleteInfoList = chunk.getItems();

        if (ebayCompleteInfoList.isEmpty()) {
            return;
        }

        // 提前获取所有 Mapper 和 Repo 实例
        AvailabilityMapper availabilityMapper = mapperManager.getMapper(AvailabilityMapper.class);
        AvailabilityRepo availabilityRepo = repoManager.getRepo(AvailabilityRepo.class);

        EbayAdditionalInfoMapper additionalInfoMapper = mapperManager.getMapper(EbayAdditionalInfoMapper.class);
        EbayAdditionalInfoRepo additionalInfoRepo = repoManager.getRepo(EbayAdditionalInfoRepo.class);

        LocationMapper locationMapper = mapperManager.getMapper(LocationMapper.class);
        LocationRepo locationRepo = repoManager.getRepo(LocationRepo.class);

        PaymentInfoMapper paymentInfoMapper = mapperManager.getMapper(PaymentInfoMapper.class);
        PaymentInfoRepo paymentInfoRepo = repoManager.getRepo(PaymentInfoRepo.class);

        VehiclePostInfoMapper postInfoMapper = mapperManager.getMapper(VehiclePostInfoMapper.class);
        PostRepo postRepo = repoManager.getRepo(PostRepo.class);

        EbaySellerMapper sellerMapper = mapperManager.getMapper(EbaySellerMapper.class);
        SellerRepo sellerRepo = repoManager.getRepo(SellerRepo.class);

        TaxMapper taxMapper = mapperManager.getMapper(TaxMapper.class);
        TaxRepo taxRepo = repoManager.getRepo(TaxRepo.class);

        VehicleInfoEntityMapper vehicleInfoMapper = mapperManager.getMapper(VehicleInfoEntityMapper.class);
        VehicleBasicInfoRepo vehicleInfoRepo = repoManager.getRepo(VehicleBasicInfoRepo.class);

        VehiclePriceMapper vehiclePriceMapper = mapperManager.getMapper(VehiclePriceMapper.class);
        VehiclePriceRepo vehiclePriceRepo = repoManager.getRepo(VehiclePriceRepo.class);

        VehicleImageMapper vehicleImageMapper = mapperManager.getMapper(VehicleImageMapper.class);
        VehicleImageRepo vehicleImageRepo = repoManager.getRepo(VehicleImageRepo.class);

        // 批量保存集合初始化
        List<AvailabilityEntity> availabilityEntities = new ArrayList<>();
        List<LocationEntity> locationEntities = new ArrayList<>();
        Set<SellerEntity> sellerEntities = new HashSet<>();
        List<TaxEntity> allTaxEntities = new ArrayList<>();
        List<VehicleBasicInfoEntity> vehicleBasicInfoEntities = new ArrayList<>();
        List<VehiclePriceEntity> allVehiclePriceEntities = new ArrayList<>();
        List<EbayAdditionalInfoEntity> additionalInfoEntities = new ArrayList<>();
        List<PaymentInfoEntity> allPaymentInfoEntities = new ArrayList<>();
        List<PostEntity> postEntities = new ArrayList<>();
        List<VehicleImageEntity> allVehicleImageEntities = new ArrayList<>();

        // 处理每个 ebayCompleteInfo 实例并收集数据
        for (List<EbayCompleteInfo> list : ebayCompleteInfoList) {
            if (list == null || list.isEmpty()) {
                continue;
            }

            for (EbayCompleteInfo ebayCompleteInfo : list) {
                if (ebayCompleteInfo == null) {
                    continue;
                }

                // 收集 availability 数据
                if (ebayCompleteInfo.getAvailability() != null) {
                    availabilityEntities.addAll(availabilityMapper.toAvailabilityEntities(ebayCompleteInfo.getAvailability()));
                }

                // 收集 location 数据
                if (ebayCompleteInfo.getLocation() != null) {
                    LocationEntity locationEntity = locationMapper.toLocationEntity(ebayCompleteInfo.getLocation());
                    locationEntities.add(locationEntity);
                }

                // 收集 seller 数据
                SellerEntity sellerEntity = null;
                if (ebayCompleteInfo.getSeller() != null) {
                    sellerEntity = sellerMapper.toSellerEntity(ebayCompleteInfo.getSeller());
                    if (sellerEntities.contains(sellerEntity)) {
                        // 通过 Stream 找到集合中相等的 sellerEntity，并赋值给 sellerEntity
                        SellerEntity finalSellerEntity = sellerEntity;
                        sellerEntity = sellerEntities.stream()
                                .filter(existingSeller -> existingSeller.equals(finalSellerEntity))
                                .findFirst() // 找到第一个匹配的元素
                                .orElse(sellerEntity); // 如果找不到则返回原来的 sellerEntity
                    } else {
                        // 如果集合中不包含该 sellerEntity，就将其添加到集合中
                        sellerEntities.add(sellerEntity);
                    }
                }

                // 收集 vehicle 信息和价格
                if (ebayCompleteInfo.getVehicleBasicInfo() != null) {
                    VehicleBasicInfoEntity vehicleBasicInfoEntity = vehicleInfoMapper.toVehicleBasicInfoEntity(ebayCompleteInfo.getVehicleBasicInfo());
                    vehicleBasicInfoEntity.setLocationEntity(locationEntities.get(locationEntities.size() - 1));
                    vehicleBasicInfoEntity.setSeller(sellerEntity);
                    vehicleBasicInfoEntities.add(vehicleBasicInfoEntity);

                    // 设置 Availability 信息
                    if (!availabilityEntities.isEmpty()) {
                        AvailabilityEntity availabilityEntity = availabilityEntities.get(availabilityEntities.size() - 1);
                        availabilityEntity.setVehicleBasicInfo(vehicleBasicInfoEntity);
                        vehicleBasicInfoEntity.setAvailability(availabilityEntity);
                    }

                    // 收集 price 数据并关联 vehicle
                    if (ebayCompleteInfo.getVehiclePrices() != null) {
                        List<VehiclePriceEntity> vehiclePriceEntities = vehiclePriceMapper.toVehiclePriceEntities(ebayCompleteInfo.getVehiclePrices());
                        vehiclePriceEntities.forEach(priceEntity -> priceEntity.setVehicle(vehicleBasicInfoEntities.get(vehicleBasicInfoEntities.size() - 1)));
                        allVehiclePriceEntities.addAll(vehiclePriceEntities);
                    }
                }

                // 收集 additional info 数据并关联 vehicle
                if (ebayCompleteInfo.getAdditionalInfo() != null) {
                    EbayAdditionalInfoEntity ebayAdditionalInfoEntity = additionalInfoMapper.toEbayAdditionalInfoEntity(ebayCompleteInfo.getAdditionalInfo());
                    ebayAdditionalInfoEntity.setVehicle(vehicleBasicInfoEntities.isEmpty() ? null : vehicleBasicInfoEntities.get(vehicleBasicInfoEntities.size() - 1));
                    additionalInfoEntities.add(ebayAdditionalInfoEntity);
                }

                // 收集 payment info 数据并关联 vehicle
                if (ebayCompleteInfo.getPaymentInfo() != null) {
                    List<PaymentInfoEntity> paymentInfoEntities = paymentInfoMapper.toPaymentInfoEntities(ebayCompleteInfo.getPaymentInfo());
                    if (!vehicleBasicInfoEntities.isEmpty()) {
                        paymentInfoEntities.forEach(paymentInfoEntity -> paymentInfoEntity.setVehicle(vehicleBasicInfoEntities.get(vehicleBasicInfoEntities.size() - 1)));
                    }
                    allPaymentInfoEntities.addAll(paymentInfoEntities);
                }

                // 收集 post info 数据并关联 seller
                if (ebayCompleteInfo.getVehiclePostInfo() != null) {
                    PostEntity postEntity = postInfoMapper.toPostEntity(ebayCompleteInfo.getVehiclePostInfo());
                    if (!sellerEntities.isEmpty()) {
                        postEntity.setSeller(sellerEntity);
                        postEntity.setVehicle(vehicleBasicInfoEntities.get(vehicleBasicInfoEntities.size() - 1));
                    }
                    postEntities.add(postEntity);
                }

                // 收集 tax 数据并关联 vehicle
                if (ebayCompleteInfo.getTax() != null) {
                    List<TaxEntity> taxEntities = taxMapper.toTaxEntities(ebayCompleteInfo.getTax());
                    taxEntities.forEach(taxEntity -> taxEntity.setVehicle(vehicleBasicInfoEntities.get(vehicleBasicInfoEntities.size() - 1)));
                    if (!taxEntities.isEmpty()) {
                        allTaxEntities.addAll(taxEntities);
                    }
                }

                if (ebayCompleteInfo.getVehicleImages() != null && !ebayCompleteInfo.getVehicleImages().isEmpty()) {
                    List<VehicleImageEntity> vehicleImageEntities = vehicleImageMapper.toVehicleImageEntityList(ebayCompleteInfo.getVehicleImages());
                    vehicleImageEntities.forEach(vehicleImageEntity -> vehicleImageEntity.setVehicle(vehicleBasicInfoEntities.get(vehicleBasicInfoEntities.size() - 1)));
                    if (!vehicleImageEntities.isEmpty()) {
                        allVehicleImageEntities.addAll(vehicleImageEntities);
                    }
                }

            }
        }

        logger.info("EbayProductFetchAndProcessItemWriter - Total vehicle will be stored in DB: {}", vehicleBasicInfoEntities.size());
        saveEntities(locationEntities, null, locationRepo);

        // Get sellers from DB
        List<String> sellerUsernames = sellerEntities.stream().map(SellerEntity::getUsername).toList();

        List<SellerEntity> sellersInDB = sellerRepo.findByPlatformAndUsernames(EBAY_PLATFORM, sellerUsernames);

        for (VehicleBasicInfoEntity vehicleBasicInfoEntity : vehicleBasicInfoEntities) {
            SellerEntity sellerInVehicle = vehicleBasicInfoEntity.getSeller(); // 获取当前 vehicle 的 seller

            if (sellerInVehicle != null) {
                // 从 sellersInDB 中找到与 vehicle 中 seller 相等的 db 中 seller
                // 替换为数据库中的 seller 实体
                sellersInDB.stream()
                        .filter(dbSeller -> dbSeller.equals(sellerInVehicle))
                        .findFirst()
                        .ifPresent(vehicleBasicInfoEntity::setSeller);
            }
        }
        // Save sellers to DB
        saveEntities(List.copyOf(sellerEntities), sellersInDB, sellerRepo);


        saveEntities(allTaxEntities, null, taxRepo);
        saveEntities(vehicleBasicInfoEntities, null, vehicleInfoRepo);
        saveEntities(allVehiclePriceEntities, null, vehiclePriceRepo);
        saveEntities(additionalInfoEntities, null, additionalInfoRepo);
        saveEntities(allPaymentInfoEntities, null, paymentInfoRepo);

        for (PostEntity postEntity : postEntities) {
            SellerEntity sellerInPost = postEntity.getSeller(); // 获取当前 vehicle 的 seller

            if (sellerInPost != null) {
                // 从 sellersInDB 中找到与 vehicle 中 seller 相等的 db 中 seller
                // 替换为数据库中的 seller 实体
                sellersInDB.stream()
                        .filter(dbSeller -> dbSeller.equals(sellerInPost))
                        .findFirst()
                        .ifPresent(postEntity::setSeller);
            }
        }

        saveEntities(postEntities, null, postRepo);
        saveEntities(allVehicleImageEntities, null, vehicleImageRepo);
        saveEntities(availabilityEntities, null, availabilityRepo);

        // Flush redis and store everything in cache as persistent storage
        redisService.persistCacheToRedis();
        // Clear all temporary storages
        redisService.clearAllTempData();
    }

    private <T> void saveEntities(List<T> entities, List<T> existInDB, JpaRepository<T, ?> jpaRepository) {
        if (entities != null && !entities.isEmpty()) {

            // 根据已有数据，区分需要插入和更新的实体
            List<T> toInsert = new ArrayList<>();
            if (existInDB != null && !existInDB.isEmpty()) {
                for (T entity : entities) {
                    if (!existInDB.contains(entity)) {
                        toInsert.add(entity);
                    }
                }
            } else {
                toInsert.addAll(entities);
            }

            // 分别进行批量插入和更新
            if (!toInsert.isEmpty()) {
                try {
                    jpaRepository.saveAll(toInsert);
                } catch (DataIntegrityViolationException e) {
                    logger.error("Data Integrity Violation: {}", e.getMessage());
                    runWhenFailed(entities);
                } catch (JpaSystemException e) {
                    logger.error("JPA System Exception: {}", e.getMessage());
                    runWhenFailed(entities);
                } catch (Exception e) {
                    logger.error("Unexpected Exception: {}", e.getMessage());
                    runWhenFailed(entities);
                }
            }
        }
    }

    private <T> void runWhenFailed(List<T> entities) {
        logger.info("Failed entities are :{}", entities);
        stepExecution.setTerminateOnly();
        throw new RuntimeException("Batch job terminated due to entity save failure.");
    }
}
