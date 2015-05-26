<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:android="http://schemas.android.com/apk/res/android">

    <xsl:param name="package" />
	<!--	<xsl:strip-space elements="*" />-->
	<xsl:output indent="yes" />
	<xsl:template match="comment()" />

	<xsl:template match="permission[@android:name='COM.PACKAGE.NAME.permission.C2D_MESSAGE']" >
		<permission android:protectionLevel="signature">
			<xsl:attribute name="android:name">
				<xsl:value-of select="concat($package,'.permission.C2D_MESSAGE')" />
			</xsl:attribute>
		</permission>
	</xsl:template>

	<xsl:template match="uses-permission[@android:name='COM.PACKAGE.NAME.permission.C2D_MESSAGE']" >
		<uses-permission>
			<xsl:attribute name="android:name">
				<xsl:value-of select="concat($package,'.permission.C2D_MESSAGE')" />
			</xsl:attribute>
		</uses-permission>
	</xsl:template>

	<xsl:template match="@*|node()">
		<xsl:copy>
			<xsl:apply-templates select="@*|node()" />
		</xsl:copy>
	</xsl:template>

</xsl:stylesheet>
