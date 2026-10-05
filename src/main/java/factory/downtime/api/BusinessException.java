package factory.downtime.api;

/**
 * 业务校验异常,由 ExceptionMapper 统一转成 400 响应。
 */
public class BusinessException extends RuntimeException {

    public final String code;

    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
    }

    public static BusinessException of(String message) {
        return new BusinessException("BUSINESS_ERROR", message);
    }
}
