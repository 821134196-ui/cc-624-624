package com.factory.downtime.resource;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

/** 业务校验异常统一返回 400 + 中文消息 */
@Provider
public class BusinessExceptionMapper implements ExceptionMapper<RuntimeException> {

    private static final Logger LOG = Logger.getLogger(BusinessExceptionMapper.class);

    public record ErrorBody(String message) {
    }

    @Override
    public Response toResponse(RuntimeException ex) {
        if (ex instanceof IllegalArgumentException || ex instanceof IllegalStateException) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(new ErrorBody(ex.getMessage()))
                    .build();
        }
        if (ex instanceof BadRequestException bre) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(new ErrorBody(bre.getMessage()))
                    .build();
        }
        LOG.error("未处理的服务端异常", ex);
        return Response.serverError()
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErrorBody("服务器内部错误: " + ex.getMessage()))
                .build();
    }
}
