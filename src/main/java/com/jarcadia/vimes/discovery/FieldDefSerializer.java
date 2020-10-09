//package com.jarcadia.vimes.discovery;
//
//import com.fasterxml.jackson.core.JsonGenerator;
//import com.fasterxml.jackson.databind.SerializerProvider;
//import com.fasterxml.jackson.databind.ser.std.StdSerializer;
//
//import java.io.IOException;
//
//public class FieldDefSerializer extends StdSerializer<FieldDefBuilder> {
//
//    protected FieldDefSerializer(Class<FieldDefBuilder> t) {
//        super(t);
//    }
//
//    @Override
//    public void serialize(FieldDefBuilder builder, JsonGenerator json,
//            SerializerProvider serializerProvider) throws IOException {
//        json.writeStartObject();
//        json.writeStringField("name", builder.getName());
//        json.writeObjectField("value", builder.getValue());
//        json.writeBooleanField("published", builder.isPublished());
//        json.writeStringField("displayName", builder.getDisplayName());
//        json.writeBooleanField("writable", builder.isWritable());
//        json.writeStringField("type", builder.getType().name());
//        json.writeBooleanField("monitored", builder.isMonitored());
//        json.writeBooleanField("increasingRange", builder.isIncreasingRange());
//        json.writeBooleanField("decreasingRange", builder.isDecreasingRange());
//        json.writeObjectField("panic", builder.getPanic());
//        json.writeObjectField("critical", builder.getCritical());
//        json.writeObjectField("warn", builder.getWarn());
//        json.writeObjectField("attention", builder.getAttention());
//        json.writeEndObject();
//    }
//}
