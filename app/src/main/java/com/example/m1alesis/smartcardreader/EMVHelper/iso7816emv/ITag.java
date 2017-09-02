package com.example.m1alesis.smartcardreader.EMVHelper.iso7816emv;

import com.example.m1alesis.smartcardreader.EMVHelper.enums.TagTypeEnum;
import com.example.m1alesis.smartcardreader.EMVHelper.enums.TagValueTypeEnum;


public interface ITag {

	enum Class {
		UNIVERSAL, APPLICATION, CONTEXT_SPECIFIC, PRIVATE
	}

	boolean isConstructed();

	byte[] getTagBytes();

	String getName();

	String getDescription();

	TagTypeEnum getType();

	TagValueTypeEnum getTagValueType();

	Class getTagClass();

	int getNumTagBytes();

}
