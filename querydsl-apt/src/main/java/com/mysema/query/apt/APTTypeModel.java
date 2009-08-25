/*
 * Copyright (c) 2009 Mysema Ltd.
 * All rights reserved.
 * 
 */
package com.mysema.query.apt;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ErrorType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.NoType;
import javax.lang.model.type.NullType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.TypeVisitor;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.Elements;

import com.mysema.query.annotations.Literal;
import com.mysema.query.codegen.InspectingTypeModel;
import com.mysema.query.codegen.TypeCategory;
import com.mysema.query.codegen.TypeModel;
import com.mysema.query.util.TypeUtil;

/**
 * APTTypeModel is a helper type for determing types of fields and methods
 * 
 * @author tiwe
 * @version $Id$
 */
public final class APTTypeModel extends InspectingTypeModel implements TypeVisitor<Void,Elements> {
    
    private static Map<TypeMirror,TypeModel> cache = new HashMap<TypeMirror,TypeModel>();
    
    public static TypeModel create(TypeMirror key, Elements el){
        if (cache.containsKey(key)){
            return cache.get(key);
        }else{
            TypeModel value = new APTTypeModel(key, el);
            cache.put(key, value);
            return value;
        }
    }
    
    private APTTypeModel(TypeMirror type, Elements el) {
        type.accept(this,el);
    }

    public String toString() {
        return name;
    }

    @Override
    public Void visitArray(ArrayType arg0, Elements el) {
        TypeModel valueInfo = APTTypeModel.create(arg0.getComponentType(), el);
        handleArray(valueInfo);
        return null;
    }

    @Override
    public Void visitDeclared(DeclaredType arg0, Elements el) {
        if (arg0.asElement() != null && arg0.asElement() instanceof TypeElement){
            TypeElement typeElement = (TypeElement)arg0.asElement();
            name = typeElement.getQualifiedName().toString();
            packageName = el.getPackageOf(typeElement).getQualifiedName().toString();
            simpleName = typeElement.getSimpleName().toString();
            if (typeElement.getKind() == ElementKind.CLASS){
                try {
                    if (typeElement.getAnnotation(Literal.class) != null) {
                        boolean comparable = false;
                        for (TypeMirror tm : typeElement.getInterfaces()){
                            TypeModel type = APTTypeModel.create(tm, el);
                            if (type.getName().equals("java.lang.Comparable")){
                                comparable = true;
                                break;
                            }
                        }                        
                        if (comparable) {
                            typeCategory =  TypeCategory.COMPARABLE;
                        } else {
                            typeCategory = TypeCategory.SIMPLE;
                        }
                    }else{
                        typeCategory = TypeCategory.get(name);    
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e.getMessage(), e);
                }
                
            }else if (arg0.asElement().getKind() == ElementKind.INTERFACE){               
                Iterator<? extends TypeMirror> i = arg0.getTypeArguments().iterator();
                Class<?> cl = TypeUtil.safeForName(name);
                if (cl == null) { // class not available
                    typeCategory = TypeCategory.get(name);
                    
                }else if (Map.class.isAssignableFrom(cl)){
                    if (!i.hasNext()){
                        throw new TypeArgumentsException(simpleName);
                    }                    
                    handleMap(APTTypeModel.create(i.next(), el), APTTypeModel.create(i.next(), el));

                } else if (List.class.isAssignableFrom(cl)) {
                    if (!i.hasNext()){
                        throw new TypeArgumentsException(simpleName);
                    }                    
                    handleList(APTTypeModel.create(i.next(), el));
                    
                } else if (Collection.class.isAssignableFrom(cl)) {
                    if (!i.hasNext()){
                        throw new TypeArgumentsException(simpleName);
                    }                    
                    handleCollection(APTTypeModel.create(i.next(), el));
                
                }else{
                    typeCategory = TypeCategory.get(name);
                }
                
            }else if (arg0.asElement().getKind() == ElementKind.ENUM){
                typeCategory = TypeCategory.SIMPLE;
            }
            
        }else{
            throw new IllegalArgumentException("Unsupported element type " + arg0.asElement());
        }
        return null;
    }
    
    @Override
    public Void visitPrimitive(PrimitiveType arg0, Elements el) {
        Class<?> cl = null;
        switch (arg0.getKind()) {
        case BOOLEAN:
            cl = Boolean.class;
            break;
        case BYTE:
            cl = Byte.class;
            break;
        case CHAR:
            cl = Character.class;
            break;
        case DOUBLE:
            cl = Double.class;
            break;
        case FLOAT:
            cl = Float.class;
            break;
        case INT:
            cl = Integer.class;
            break;
        case LONG:
            cl = Long.class;
            break;
        case SHORT:
            cl = Short.class;
            break;
        }
        typeCategory = TypeCategory.get(cl.getName());
        setNames(cl);
        return null;
    }


    @Override
    public Void visitTypeVariable(TypeVariable arg0, Elements el) {
        if (arg0.getUpperBound() != null) {
            TypeModel lb = APTTypeModel.create(arg0.getUpperBound(), el);
            setNames(lb);
            typeCategory = lb.getTypeCategory();
        }
        return null;
    }

    @Override
    public Void visitWildcard(WildcardType arg0, Elements el) {
        if (arg0.getExtendsBound() != null) {
            TypeModel lb = APTTypeModel.create(arg0.getExtendsBound(), el);
            setNames(lb);
            typeCategory = lb.getTypeCategory();
        }
        return null;
    }

    @Override
    public Void visit(TypeMirror t) {
        return t.accept(this, null);
    }

    @Override
    public Void visit(TypeMirror t, Elements el) {
        return t.accept(this, el);
    }

    @Override
    public Void visitError(ErrorType t, Elements el) {
        return null;
    }

    @Override
    public Void visitExecutable(ExecutableType t, Elements el) {
        return null;
    }

    @Override
    public Void visitNoType(NoType t, Elements p) {
        return null;
    }

    @Override
    public Void visitNull(NullType t, Elements p) {
        return null;
    }

    @Override
    public Void visitUnknown(TypeMirror t, Elements p) {
        return null;
    }

}
