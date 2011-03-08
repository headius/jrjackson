package org.codehaus.jackson.map.deser;

import java.io.IOException;

import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonToken;
import org.codehaus.jackson.map.*;
import org.codehaus.jackson.map.annotate.JacksonStdImpl;
import org.codehaus.jackson.map.type.ArrayType;
import org.codehaus.jackson.map.util.ObjectBuffer;
import org.codehaus.jackson.type.JavaType;

/**
 * Basic serializer that can serialize non-primitive arrays.
 */
@JacksonStdImpl
public class ArrayDeserializer
    extends ContainerDeserializer<Object[]>
{
    // // Configuration

    protected final JavaType _arrayType;

    /**
     * Flag that indicates whether the component type is Object or not.
     * Used for minor optimization when constructing result.
     */
    protected final boolean _untyped;

    /**
     * Type of contained elements: needed for constructing actual
     * result array
     */
    protected final Class<?> _elementClass;

    /**
     * Element deserializer
     */
    protected final JsonDeserializer<Object> _elementDeserializer;

    /**
     * If element instances have polymorphic type information, this
     * is the type deserializer that can handle it
     */
    final TypeDeserializer _elementTypeDeserializer;

    @Deprecated
    public ArrayDeserializer(ArrayType arrayType, JsonDeserializer<Object> elemDeser)
    {
        this(arrayType, elemDeser, null);
    }

    public ArrayDeserializer(ArrayType arrayType, JsonDeserializer<Object> elemDeser,
            TypeDeserializer elemTypeDeser)
    {
        super(Object[].class);
        _arrayType = arrayType;
        _elementClass = arrayType.getContentType().getRawClass();
        _untyped = (_elementClass == Object.class);
        _elementDeserializer = elemDeser;
        _elementTypeDeserializer = elemTypeDeser;
    }

    /*
    /**********************************************************
    /* ContainerDeserializer API
    /**********************************************************
     */

    @Override
    public JavaType getContentType() {
        return _arrayType.getContentType();
    }

    @Override
    public JsonDeserializer<Object> getContentDeserializer() {
        return _elementDeserializer;
    }

    /*
    /**********************************************************
    /* JsonDeserializer API
    /**********************************************************
     */

    @Override
    public Object[] deserialize(JsonParser jp, DeserializationContext ctxt)
        throws IOException, JsonProcessingException
    {
        // Ok: must point to START_ARRAY (or equivalent)
        if (!jp.isExpectedStartArrayToken()) {
            /* 04-Oct-2009, tatu: One exception; byte arrays are generally
             *   serialized as base64, so that should be handled
             */
            if (jp.getCurrentToken() == JsonToken.VALUE_STRING
                && _elementClass == Byte.class) {
                return deserializeFromBase64(jp, ctxt);
            }
            throw ctxt.mappingException(_arrayType.getRawClass());
        }

        final ObjectBuffer buffer = ctxt.leaseObjectBuffer();
        Object[] chunk = buffer.resetAndStart();
        int ix = 0;
        JsonToken t;
        final TypeDeserializer typeDeser = _elementTypeDeserializer;

        while ((t = jp.nextToken()) != JsonToken.END_ARRAY) {
            // Note: must handle null explicitly here; value deserializers won't
            Object value;

            if (t == JsonToken.VALUE_NULL) {
                value = null;
            } else if (typeDeser == null) {
                value = _elementDeserializer.deserialize(jp, ctxt);
            } else {
                value = _elementDeserializer.deserializeWithType(jp, ctxt, typeDeser);
            }
            if (ix >= chunk.length) {
                chunk = buffer.appendCompletedChunk(chunk);
                ix = 0;
            }
            chunk[ix++] = value;
        }

        Object[] result;

        if (_untyped) {
            result = buffer.completeAndClearBuffer(chunk, ix);
        } else {
            result = buffer.completeAndClearBuffer(chunk, ix, _elementClass);
        }
        ctxt.returnObjectBuffer(buffer);
        return result;
    }

    @Override
    public Object[] deserializeWithType(JsonParser jp, DeserializationContext ctxt,
            TypeDeserializer typeDeserializer)
        throws IOException, JsonProcessingException
    {
        /* Should there be separate handling for base64 stuff?
         * for now this should be enough:
         */
        return (Object[]) typeDeserializer.deserializeTypedFromArray(jp, ctxt);
    }

    /*
    /**********************************************************
    /* Internal methods
    /**********************************************************
     */

    protected Byte[] deserializeFromBase64(JsonParser jp, DeserializationContext ctxt)
        throws IOException, JsonProcessingException
    {
        // First same as what ArrayDeserializers.ByteDeser does:
        byte[] b = jp.getBinaryValue(ctxt.getBase64Variant());
        // But then need to convert to wrappers
        Byte[] result = new Byte[b.length];
        for (int i = 0, len = b.length; i < len; ++i) {
            result[i] = Byte.valueOf(b[i]);
        }
        return result;
    }
}
      #field = jp.getText
      #jp.nextToken
      #result[field] = deserialize(jp, ctxt)
      #result[jp.getText] = deserialize((jp.nextToken; jp), ctxt)
      #return result if jp.nextToken != JsonToken::FIELD_NAME

      #field = jp.getText
      #jp.nextToken
      #result[field] = deserialize(jp, ctxt)
      #result[jp.getText] = deserialize((jp.nextToken; jp), ctxt)
      #return result if jp.nextToken() != JsonToken::FIELD_NAME

      # And then the general case
