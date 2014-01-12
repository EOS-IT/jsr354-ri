/*
 * CREDIT SUISSE IS WILLING TO LICENSE THIS SPECIFICATION TO YOU ONLY UPON THE CONDITION THAT YOU
 * ACCEPT ALL OF THE TERMS CONTAINED IN THIS AGREEMENT. PLEASE READ THE TERMS AND CONDITIONS OF THIS
 * AGREEMENT CAREFULLY. BY DOWNLOADING THIS SPECIFICATION, YOU ACCEPT THE TERMS AND CONDITIONS OF
 * THE AGREEMENT. IF YOU ARE NOT WILLING TO BE BOUND BY IT, SELECT THE "DECLINE" BUTTON AT THE
 * BOTTOM OF THIS PAGE. Specification: JSR-354 Money and Currency API ("Specification") Copyright
 * (c) 2012-2013, Credit Suisse All rights reserved.
 */
package org.javamoney.moneta.format.internal;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import javax.money.CurrencyUnit;
import javax.money.MonetaryAmount;
import javax.money.MonetaryAmountFactory;
import javax.money.MonetaryAmounts;
import javax.money.MonetaryContext;
import javax.money.format.AmountStyle;
import javax.money.format.MonetaryAmountFormat;
import javax.money.format.MonetaryParseException;
import javax.money.spi.Bootstrap;
import javax.money.spi.MonetaryLogger;

/**
 * Formats instances of {@code MonetaryAmount} to a {@link String} or an {@link Appendable}.
 * <p>
 * Instances of this class are not thread-safe. Basically when using {@link MonetaryAmountFormat}
 * instances a new instance should be created on each access.
 * 
 * @author Anatole Tresch
 * @author Werner Keil
 * 
 */
final class DefaultMonetaryAmountFormat implements MonetaryAmountFormat {

	/** The international Unicode currency sign. */
	private static final char CURRENCY_SIGN = '\u00A4';

	/** The tokens to be used for formatting/parsing of positive and zero numbers. */
	private List<FormatToken> positiveTokens;

	/** The tokens to be used for formatting/parsing of positive and zero numbers. */
	private List<FormatToken> negativeTokens;

	/**
	 * The {@link MonetaryContext} applied on creating a {@link MonetaryAmount} based on data
	 * parsed.
	 */
	private MonetaryContext monetaryContext = MonetaryAmounts
			.getAmountFactory()
			.getDefaultMonetaryContext();

	/** Currency used, when no currency was on the input parsed. */
	private CurrencyUnit defaultCurrency;

	/** The current {@link AmountStyle}, never null. */
	private AmountStyle amountStyle;

	/**
	 * Creates a new instance.
	 * 
	 * @param style
	 *            the {@link AmountStyle} to be used, not {@code null}.
	 * @param currencyStyle
	 *            the style defining how the {@link CurrencyUnit} should be formatted.
	 * @param currencyPlacement
	 *            Defines how where the {@link CurrencyUnit} should be placed in relation to the
	 *            numeric part or the {@link MonetaryAmount}.
	 * @param monetaryContext
	 *            The {@link MonetaryContext} used for creating of {@link MonetaryAmount} instances
	 *            during parsing.
	 * @param defaultCurrency
	 *            The default {@link CurrencyUnit} used, when no currency information can be
	 *            extracted from the parse input.
	 */
	DefaultMonetaryAmountFormat(AmountStyle style) {
		setAmountStyle(style);
	}

	private void initPattern(String pattern, List<FormatToken> tokens,
			AmountStyle style) {
		int index = pattern.indexOf(CURRENCY_SIGN);
		if (index > 0) { // currency placement after, between
			String p1 = pattern.substring(0, index);
			String p2 = pattern.substring(index + 1);
			if (isLiteralPattern(p1, style)) {
				tokens.add(new LiteralToken(p1));
				tokens.add(new CurrencyToken(style.getCurrencyStyle(), style
						.getLocale()));
			}
			else {
				tokens.add(new AmountNumberToken(style, p1));
				tokens.add(new CurrencyToken(style.getCurrencyStyle(), style
						.getLocale()));
			}
			if (!p2.isEmpty()) {
				if (isLiteralPattern(p2, style)) {
					tokens.add(new LiteralToken(p2));
				}
				else {
					tokens.add(new AmountNumberToken(style, p2));
				}
			}
		}
		else if (index == 0) { // currency placement before
			tokens.add(new CurrencyToken(style.getCurrencyStyle(), style
					.getLocale()));
			tokens.add(new AmountNumberToken(style, pattern.substring(1)));
		}
		else { // no currency
			tokens.add(new AmountNumberToken(style, pattern));
		}
	}

	private boolean isLiteralPattern(String pattern, AmountStyle style) {
		// TODO implement better here
		if (pattern.contains("#")) {
			return false;
		}
		return true;
	}

	/**
	 * Formats a value of {@code T} to a {@code String}. The {@link Locale} passed defines the
	 * overall target {@link Locale}, whereas the {@link LocalizationStyle} attached with the
	 * instances configures, how the {@link MonetaryAmountFormat} should generally behave. The
	 * {@link LocalizationStyle} allows to configure the formatting and parsing in arbitrary
	 * details. The attributes that are supported are determined by the according
	 * {@link MonetaryAmountFormat} implementation:
	 * <ul>
	 * <li>When the {@link MonetaryAmountFormat} was created using the {@link Builder} , all the
	 * {@link FormatToken}, that model the overall format, and the {@link ItemFactory}, that is
	 * responsible for extracting the final parsing result, returned from a parsing call, are all
	 * possible recipients for attributes of the configuring {@link LocalizationStyle}.
	 * <li>When the {@link MonetaryAmountFormat} was provided by an instance of
	 * {@link ItemFormatFactorySpi} the {@link MonetaryAmountFormat} returned determines the
	 * capabilities that can be configured.
	 * </ul>
	 * 
	 * So, regardless if an {@link MonetaryAmountFormat} is created using the fluent style
	 * {@link Builder} pattern, or provided as preconfigured implementation,
	 * {@link LocalizationStyle}s allow to configure them both effectively.
	 * 
	 * @param amount
	 *            the amount to print, not {@code null}
	 * @return the string printed using the settings of this formatter
	 * @throws UnsupportedOperationException
	 *             if the formatter is unable to print
	 */
	public String format(MonetaryAmount amount) {
		StringBuilder builder = new StringBuilder();
		try {
			print(builder, amount);
		} catch (IOException e) {
			throw new IllegalStateException("Error foratting of " + amount, e);
		}
		return builder.toString();
	}

	/**
	 * Prints a item value to an {@code Appendable}.
	 * <p>
	 * Example implementations of {@code Appendable} are {@code StringBuilder}, {@code StringBuffer}
	 * or {@code Writer}. Note that {@code StringBuilder} and {@code StringBuffer} never throw an
	 * {@code IOException}.
	 * 
	 * @param appendable
	 *            the appendable to add to, not null
	 * @param item
	 *            the item to print, not null
	 * @param locale
	 *            the main target {@link Locale} to be used, not {@code null}
	 * @throws UnsupportedOperationException
	 *             if the formatter is unable to print
	 * @throws ItemFormatException
	 *             if there is a problem while printing
	 * @throws IOException
	 *             if an IO error occurs
	 */
	public void print(Appendable appendable, MonetaryAmount amount)
			throws IOException {
		if (amount.isNegative()) {
			for (FormatToken token : negativeTokens) {
				token.print(appendable, amount);
			}
		}
		else {
			for (FormatToken token : positiveTokens) {
				token.print(appendable, amount);
			}
		}
	}

	/**
	 * Fully parses the text into an instance of {@code T}.
	 * <p>
	 * The parse must complete normally and parse the entire text. If the parse completes without
	 * reading the entire length of the text, an exception is thrown. If any other problem occurs
	 * during parsing, an exception is thrown.
	 * <p>
	 * This method uses a {@link Locale} as an input parameter. Additionally the
	 * {@link ItemFormatException} instance is configured by a {@link LocalizationStyle}.
	 * {@link LocalizationStyle}s allows to configure formatting input in detail. This allows to
	 * implement complex formatting requirements using this interface.
	 * 
	 * @param text
	 *            the text to parse, not null
	 * @param locale
	 *            the main target {@link Locale} to be used, not {@code null}
	 * @return the parsed value, never {@code null}
	 * @throws UnsupportedOperationException
	 *             if the formatter is unable to parse
	 * @throws ItemParseException
	 *             if there is a problem while parsing
	 */
	public MonetaryAmount parse(CharSequence text)
			throws MonetaryParseException {
		ParseContext ctx = new ParseContext(text);
		try {
			for (FormatToken token : this.positiveTokens) {
				token.parse(ctx);
			}
		} catch (Exception e) {
			// try parsing negative...
			MonetaryLogger log = Bootstrap.getService(MonetaryLogger.class);
			if (log.isDebugEnabled()) {
				log.logDebug(
						"Failed to parse positive pattern, trying negative for: "
								+ text, e);
			}
			for (FormatToken token : this.negativeTokens) {
				token.parse(ctx);
			}
		}
		CurrencyUnit unit = ctx.getParsedCurrency();
		Number num = ctx.getParsedNumber();
		if (unit == null) {
			unit = defaultCurrency;
		}
		if (num == null) {
			throw new MonetaryParseException(text.toString(), -1);
		}
		Class<? extends MonetaryAmount> type = MonetaryAmounts
				.queryAmountType(this.monetaryContext);
		if (type == null) {
			MonetaryLogger log = Bootstrap.getService(MonetaryLogger.class);
			if (log.isWarningEnabled()) {
				log.logWarning("Required moneterayContext was not resolvable, using default, required="
						+ this.monetaryContext);
			}
			type = MonetaryAmounts.getDefaultAmountType();
		}
		return MonetaryAmounts.getAmountFactory(type)
				.setContext(monetaryContext).setCurrency(unit)
				.setNumber(num)
				.create();
	}

	/*
	 * (non-Javadoc)
	 * @see javax.money.format.MonetaryAmountFormat#getMonetaryContext()
	 */
	@Override
	public MonetaryContext getMonetaryContext() {
		return monetaryContext;
	}

	/*
	 * (non-Javadoc)
	 * @see javax.money.MonetaryQuery#queryFrom(javax.money.MonetaryAmount)
	 */
	@Override
	public String queryFrom(MonetaryAmount amount) {
		return format(amount);
	}

	@Override
	public CurrencyUnit getDefaultCurrency() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setDefaultCurrency(CurrencyUnit currency) {
		this.defaultCurrency = currency;
	}

	@Override
	public AmountStyle getAmountStyle() {
		return this.amountStyle;
	}

	@Override
	public void setAmountStyle(AmountStyle style) {
		Objects.requireNonNull(style);
		this.amountStyle = style;
		this.positiveTokens = new ArrayList<>();
		this.negativeTokens = new ArrayList<>();
		String pattern = style.getPattern();
		if (pattern.indexOf(CURRENCY_SIGN) < 0) {
			this.positiveTokens.add(new AmountNumberToken(style,
					pattern));
			this.negativeTokens = positiveTokens;
		}
		else {
			// split into (potential) plus, minus patterns
			char patternSeparator = ';';
			if (style.getSymbols() != null) {
				patternSeparator = style.getSymbols()
						.getPatternSeparator();
			}
			String[] plusMinusPatterns = pattern.split("'" + patternSeparator
					+ "'");
			initPattern(plusMinusPatterns[0], this.positiveTokens,
					style);
			if (plusMinusPatterns.length > 1) {
				initPattern(plusMinusPatterns[1], this.negativeTokens,
						style);
			}
			else {
				this.negativeTokens = this.positiveTokens;
			}
		}
	}

	@Override
	public void setMonetaryContext(MonetaryContext context) {
		Objects.requireNonNull(context);
		this.monetaryContext = context;
	}

}