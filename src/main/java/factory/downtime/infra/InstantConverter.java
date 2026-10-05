package factory.downtime.infra;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.time.Instant;

/**
 * SQLite 对 TIMESTAMP 的支持不稳定,统一以 BIGINT(epoch 毫秒)存储时间。
 */
@Converter(autoApply = true)
public class InstantConverter implements AttributeConverter<Instant, Long> {

    @Override
    public Long convertToDatabaseColumn(Instant attribute) {
        return attribute == null ? null : attribute.toEpochMilli();
    }

    @Override
    public Instant convertToEntityAttribute(Long dbData) {
        return dbData == null ? null : Instant.ofEpochMilli(dbData);
    }
}
