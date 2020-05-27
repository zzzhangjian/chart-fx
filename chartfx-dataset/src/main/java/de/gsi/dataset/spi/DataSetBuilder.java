package de.gsi.dataset.spi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import de.gsi.dataset.AxisDescription;
import de.gsi.dataset.DataSet;
import de.gsi.dataset.utils.AssertUtils;

/**
 * Helps allocating new DataSets in a flexible and efficient way.
 * Values, Errors and Metadata can be added along with requirements for the resulting DataSet.
 * Values are automatically converted between float and double if needed.
 * <p>
 * Currently the builder generates the following DataSet types:
 * <ul>
 * <li>dim:2, useFloat=false, errors=false: {@link DefaultDataSet}
 * <li>dim:2, useFloat=false, errors=true: {@link DefaultErrorDataSet}
 * <li>dim:2, useFloat=true, errors=false: {@link FloatDataSet}
 * <li>dim:3+, useFloat=false, errors=false: {@link MultiDimDoubleDataSet}
 * <li>All other combinations throw a {@code UnsupportedOperationException}
 * </ul>
 * @author Alexander Krimm
 */
public class DataSetBuilder {
    protected String name;
    // double values
    protected Map<Integer, double[]> values = new HashMap<>();
    protected Map<Integer, double[]> errorsPos = new HashMap<>();
    protected Map<Integer, double[]> errorsNeg = new HashMap<>();
    // float values
    protected Map<Integer, float[]> valuesFloat = new HashMap<>();
    protected Map<Integer, float[]> errorsPosFloat = new HashMap<>();
    protected Map<Integer, float[]> errorsNegFloat = new HashMap<>();
    // size and type of dataset
    protected int[] initialCapacity = null; // number of elements for each dimensions, missing dimensions will get last index
    private int dimension = -1; // number of dimensions, -1 -> max of length of initial capacity and highest dimension where data was set
    private boolean useErrors = false; // whether the output DataSet should contain errors
    private boolean useFloat = false; // whether the output DataSet should store float values
    // MetaData
    protected List<String> infoList = new ArrayList<>();
    protected List<String> warningList = new ArrayList<>();
    protected List<String> errorList = new ArrayList<>();
    protected Map<String, String> metaInfoMap = new HashMap<>();
    // Labels and styles
    protected Map<Integer, String> dataLabels = new HashMap<>();
    protected Map<Integer, String> dataStyles = new HashMap<>();
    protected Map<Integer, AxisDescription> axisDescriptions = new HashMap<>();

    /**
     * default DataSet factory
     */
    public DataSetBuilder() {
        super();
    }

    /**
     * DataSet factory with data set name
     *
     * @param dataSetName data set name
     */
    public DataSetBuilder(final String dataSetName) {
        super();
        this.setName(dataSetName);
    }

    protected void addDataLabelStyleMap(final DataSet dataSet) {
        AssertUtils.assertType(dataSet, AbstractDataSet.class);
        AbstractDataSet<?> ds = (AbstractDataSet<?>) dataSet;
        if (!dataLabels.isEmpty()) {
            dataLabels.forEach(ds::addDataLabel);
        }
        if (!dataStyles.isEmpty()) {
            dataStyles.forEach(ds::addDataStyle);
        }
    }

    protected void addDataRanges(final DataSet dataSet) {
        for (Entry<Integer, AxisDescription> descriptionEntry : axisDescriptions.entrySet()) {
            dataSet.getAxisDescription(descriptionEntry.getKey()).set(descriptionEntry.getValue());
        }
    }

    protected void addMetaData(final DataSet dataSet) {
        AssertUtils.assertType(dataSet, AbstractDataSet.class);
        AbstractDataSet<?> ds = (AbstractDataSet<?>) dataSet;
        ds.getInfoList().addAll(infoList);
        ds.getWarningList().addAll(warningList);
        ds.getErrorList().addAll(errorList);
        ds.getMetaInfo().putAll(metaInfoMap);
    }

    /**
     * @return The requested DataSet
     */
    public DataSet build() {
        final String dsName = name == null ? ("DataSet@" + System.currentTimeMillis()) : name;
        DataSet dataSet = buildRawDataSet(dsName);

        // add meta data
        addMetaData(dataSet);

        addDataRanges(dataSet);

        addDataLabelStyleMap(dataSet);

        return dataSet;
    }

    private int getResultDimension() {
        int maxDim = values.keySet().stream().collect(Collectors.maxBy(Integer::compare)).orElse(-1);
        maxDim = Math.max(maxDim, valuesFloat.keySet().stream().collect(Collectors.maxBy(Integer::compare)).orElse(-1));
        maxDim = Math.max(maxDim, errorsNeg.keySet().stream().collect(Collectors.maxBy(Integer::compare)).orElse(-1));
        maxDim = Math.max(maxDim, errorsNegFloat.keySet().stream().collect(Collectors.maxBy(Integer::compare)).orElse(-1));
        maxDim = Math.max(maxDim, errorsPos.keySet().stream().collect(Collectors.maxBy(Integer::compare)).orElse(-1));
        maxDim = Math.max(maxDim, errorsPosFloat.keySet().stream().collect(Collectors.maxBy(Integer::compare)).orElse(-1));
        maxDim = Math.max(maxDim,
                axisDescriptions.keySet().stream().collect(Collectors.maxBy(Integer::compare)).orElse(-1));
        if (this.dimension == -1) {
            return maxDim + 1;
        } else if (this.dimension <= maxDim) {
            throw new UnsupportedOperationException("Supplied data dimensions exceed requested number of dimensions");
        }
        return this.dimension;
    }

    private int[] getResultSize(int nDims) {
        int[] result = new int[nDims];
        for (int i = 0; i < nDims; i++) {
            result[i] = getResultSize(nDims, i);
        }
        return result;
    }

    private int getResultSize(final int nDims, final int dimIndex) { // NOPMD
        if (initialCapacity != null && dimIndex < initialCapacity.length) { // use given capacity
            return initialCapacity[dimIndex];
        } else if (initialCapacity != null && initialCapacity.length > 0) { // use last given dimension
            return initialCapacity[initialCapacity.length - 1];
        }
        // not given, use longest supplied data
        int result = 0;
        if (values.containsKey(dimIndex)) {
            result = Math.max(result, values.get(dimIndex).length);
        }
        if (valuesFloat.containsKey(dimIndex)) {
            result = Math.max(result, valuesFloat.get(dimIndex).length);
        }
        if (errorsNeg.containsKey(dimIndex)) {
            result = Math.max(result, errorsNeg.get(dimIndex).length);
        }
        if (errorsNegFloat.containsKey(dimIndex)) {
            result = Math.max(result, errorsNegFloat.get(dimIndex).length);
        }
        if (errorsPos.containsKey(dimIndex)) {
            result = Math.max(result, errorsPos.get(dimIndex).length);
        }
        if (errorsPosFloat.containsKey(dimIndex)) {
            result = Math.max(result, errorsPosFloat.get(dimIndex).length);
        }
        if (dimIndex == nDims - 1) { // labels & styles only for last dimension?
            result = Math.max(result,
                    dataLabels.keySet().stream().collect(Collectors.maxBy(Integer::compare)).orElse(-1));
            result = Math.max(result,
                    dataStyles.keySet().stream().collect(Collectors.maxBy(Integer::compare)).orElse(-1));
        }
        return result;
    }

    protected DataSet buildRawDataSet(final String dsName) {
        int dim = getResultDimension();
        int[] size = getResultSize(dim);
        switch (dim) {
        case 0:
        case 1:
        case 2:
            int dataCount = 0; // 2D datasets always have same number of points for x and y
            for (int i = 0; i < dim; i++) {
                dataCount = Math.max(dataCount, size[i]);
            }
            if (errorsNeg.size() == 0 && errorsPos.size() == 0 && !this.useErrors) {
                if (useFloat) {
                    return buildDefaultDataSetFloat(dsName, dataCount);
                }
                return buildDefaultDataSet(dsName, dataCount);
            }
            if (useFloat) {
                throw new UnsupportedOperationException("No float error DataSet implemented yet");
            }
            return buildDefaultErrorDataSet(dsName, dataCount);
        default:
            if (useFloat) {
                throw new UnsupportedOperationException("Float DataSet Not implemented for nDims > 2");
            }
            return buildMultiDimDataSet(dsName, size);
        }
    }

    private DataSet buildDefaultDataSet(final String dsName, final int size) {
        double[] xvalues = getValues(DataSet.DIM_X, size);
        double[] yvalues = getValues(DataSet.DIM_Y, size);
        return new DefaultDataSet(dsName, xvalues, yvalues, size, false);
    }

    private DataSet buildDefaultDataSetFloat(final String dsName, final int size) {
        float[] xvalues = getValuesFloat(DataSet.DIM_X, size);
        float[] yvalues = getValuesFloat(DataSet.DIM_Y, size);
        return new FloatDataSet(dsName, xvalues, yvalues, size, false);
    }

    private DataSet buildDefaultErrorDataSet(final String dsName, final int size) {
        double[] xvalues = getValues(DataSet.DIM_X, size);
        double[] yvalues = getValues(DataSet.DIM_Y, size);
        if (errorsNeg.containsKey(DataSet.DIM_X) || errorsPos.containsKey(DataSet.DIM_X)) {
            throw new UnsupportedOperationException("DataSetBuilder: X Errors not implemented for 2D DataSetBuilder");
        }
        double[] yen = getErrors(DataSet.DIM_Y, size, false);
        double[] yep = getErrors(DataSet.DIM_Y, size, true);
        return new DefaultErrorDataSet(dsName, xvalues, yvalues, yen, yep, size, false);
    }

    private DataSet buildMultiDimDataSet(final String dsName, final int[] size) {
        final int nDims = size.length;
        final double[][] inputValues = new double[nDims][];
        for (int dimIndex = 0; dimIndex < nDims; dimIndex++) {
            inputValues[dimIndex] = getValues(dimIndex, size[dimIndex]);
            if (errorsNeg.containsKey(dimIndex) || errorsPos.containsKey(dimIndex) || useErrors) {
                throw new UnsupportedOperationException("DataSetBuilder: Errors not implemented for MultiDimDataSet");
            }
        }
        return new MultiDimDoubleDataSet(dsName, false, inputValues);
    }

    private double[] getValues(final int dimIndex, final int size) {
        double[] vals = this.values.get(dimIndex);
        if (vals == null && valuesFloat.containsKey(dimIndex)) {
            final float[] valsFloat = valuesFloat.get(dimIndex);
            vals = IntStream.range(0, size).mapToDouble(i -> i < valsFloat.length ? valsFloat[i] : 0.0).toArray();
        } else if (vals == null) {
            if (dimIndex == 0) {
                vals = IntStream.range(0, size).mapToDouble(x -> x).toArray();
            } else {
                vals = new double[size];
            }
        } else if (vals.length != size) {
            final double[] newVal = new double[size];
            System.arraycopy(vals, 0, newVal, 0, Math.min(vals.length, newVal.length));
            vals = newVal;
        }
        return vals;
    }

    private float[] getValuesFloat(final int dimIndex, final int size) {
        float[] vals = this.valuesFloat.get(dimIndex);
        if (vals == null && values.containsKey(dimIndex)) {
            final double[] valsDouble = values.get(dimIndex);
            vals = new float[size];
            for (int i = 0; i < size; i++) {
                vals[i] = (float) valsDouble[i];
            }
        } else if (vals == null) {
            vals = new float[size];
            if (dimIndex == 0) {
                vals = new float[size];
                for (int i = 0; i < size; i++) {
                    vals[i] = i;
                }
            }
        }
        return vals;
    }

    private double[] getErrors(final int dimIndex, final int size, final boolean pos) {
        double[] vals = pos ? errorsPos.get(dimIndex) : errorsNeg.get(dimIndex);
        float[] floats = pos ? errorsPosFloat.get(dimIndex) : errorsNegFloat.get(dimIndex);
        double[] valsAlternate = !pos ? errorsPos.get(dimIndex) : errorsNeg.get(dimIndex);
        float[] floatsAlternate = !pos ? errorsPosFloat.get(dimIndex) : errorsNegFloat.get(dimIndex);
        if (vals == null && floats != null) {
            vals = IntStream.range(0, size).mapToDouble(i -> floats[i]).toArray();
        } else if (vals == null && valsAlternate != null) {
            vals = valsAlternate;
        } else if (vals == null && floatsAlternate != null) {
            vals = IntStream.range(0, size).mapToDouble(i -> floatsAlternate[i]).toArray();
        } else if (vals == null) {
            vals = new double[size];
        }
        return vals;
    }

    private AxisDescription getAxisDescription(int dimIndex) {
        if (dimIndex < 0) {
            throw new UnsupportedOperationException("axis dimension cannot be negative]: " + dimIndex);
        }
        return axisDescriptions.computeIfAbsent(dimIndex, key -> new DefaultAxisDescription());
    }

    public DataSetBuilder setAxisMax(final int dimension, final double value) {
        getAxisDescription(dimension).setMax(value);
        return this;
    }

    public DataSetBuilder setAxisMin(final int dimension, final double value) {
        getAxisDescription(dimension).setMin(value);
        return this;
    }

    public DataSetBuilder setAxisName(final int dimension, final String name) {
        getAxisDescription(dimension).set(name);
        return this;
    }

    public DataSetBuilder setAxisUnit(final int dimension, final String unit) {
        getAxisDescription(dimension).set(getAxisDescription(dimension).getName(), unit);
        return this;
    }

    public DataSetBuilder setDataLabelMap(final Map<Integer, String> map) {
        if (map != null && !map.isEmpty()) {
            dataLabels.putAll(map);
        }
        return this;
    }

    public DataSetBuilder setDataStyleMap(final Map<Integer, String> map) {
        if (map != null && !map.isEmpty()) {
            dataStyles.putAll(map);
        }
        return this;
    }

    public DataSetBuilder setMetaErrorList(final String[] errors) {
        this.errorList.addAll(Arrays.asList(errors));
        return this;
    }

    public DataSetBuilder setMetaInfoList(final String[] infos) {
        this.infoList.addAll(Arrays.asList(infos));
        return this;
    }

    public DataSetBuilder setMetaInfoMap(final Map<String, String> map) {
        if (map != null && !map.isEmpty()) {
            metaInfoMap.putAll(map);
        }
        return this;
    }

    public DataSetBuilder setMetaWarningList(final String[] warning) {
        this.warningList.addAll(Arrays.asList(warning));
        return this;
    }

    public final DataSetBuilder setName(final String name) {
        this.name = name;
        return this;
    }

    public DataSetBuilder setNegError(final int dimIndex, final double[] errors) {
        final double[] vals = new double[errors.length];
        System.arraycopy(errors, 0, vals, 0, errors.length);
        return setNegErrorNoCopy(dimIndex, vals);
    }

    public final DataSetBuilder setNegError(final int dimIndex, final float[] errors) {
        final float[] vals = new float[errors.length];
        System.arraycopy(errors, 0, vals, 0, errors.length);
        return setNegErrorNoCopy(dimIndex, vals);
    }

    public DataSetBuilder setNegErrorNoCopy(final int dimIndex, final double[] errors) { // NOPMD
        // direct storage is on purpose
        this.errorsNeg.put(dimIndex, errors);
        return setEnableErrors(true);
    }

    public DataSetBuilder setNegErrorNoCopy(final int dimIndex, final float[] errors) { // NOPMD
        // direct storage is on purpose
        this.errorsNegFloat.put(dimIndex, errors);
        return setEnableErrors(true);
    }

    /**
     * @param dimIndex The dimension index this error is used for
     * @param errors double array with the errors (will only be copied if double DataSet is requested)
     * @return itself for method chaining
     */
    public final DataSetBuilder setPosError(final int dimIndex, final double[] errors) {
        final double[] vals = new double[errors.length];
        System.arraycopy(errors, 0, vals, 0, errors.length);
        return setPosErrorNoCopy(dimIndex, vals);
    }

    /**
     * @param dimIndex The dimension index this error is used for
     * @param errors float array with the errors (will only be copied if double DataSet is requested)
     * @return itself for method chaining
     */
    public final DataSetBuilder setPosError(final int dimIndex, final float[] errors) {
        final float[] vals = new float[errors.length];
        System.arraycopy(errors, 0, vals, 0, errors.length);
        return setPosErrorNoCopy(dimIndex, vals);
    }

    /**
     * @param dimIndex The dimension index this error is used for
     * @param errors double array with the errors (will only be copied if double DataSet is requested)
     * @return itself for method chaining
     */
    public final DataSetBuilder setPosErrorNoCopy(final int dimIndex, final double[] errors) { // NOPMD
        // direct storage is on purpose
        this.errorsPos.put(dimIndex, errors);
        return setEnableErrors(true);
    }

    /**
     * @param dimIndex The dimension index this error is used for
     * @param errors float array with the errors (will only be copied if double DataSet is requested)
     * @return itself for method chaining
     */
    public final DataSetBuilder setPosErrorNoCopy(final int dimIndex, final float[] errors) { // NOPMD
        // direct storage is on purpose
        this.errorsPosFloat.put(dimIndex, errors);
        return setEnableErrors(true);
    }

    /**
     * @param dimIndex the dim index the data is for.
     * @param values double array with data for the x dimension (will be copied)
     * @return itself for method chaining
     */
    public final DataSetBuilder setValues(final int dimIndex, final double[] values) {
        final double[] vals = new double[values.length];
        System.arraycopy(values, 0, vals, 0, values.length);
        return setValuesNoCopy(dimIndex, vals);
    }

    /**
     * @param dimIndex the dim index the data is for.
     * @param values double array with data for the x dimension (will be copied)
     * @return itself for method chaining
     */
    public final DataSetBuilder setValues(final int dimIndex, final float[] values) {
        final float[] vals = new float[values.length];
        System.arraycopy(values, 0, vals, 0, values.length);
        return setValuesNoCopy(dimIndex, vals);
    }

    /**
     * Convenience function to use data given as nested arrays.
     * Converts the nested array to a linear strided array.
     * 
     * @param dimIndex the dim index the data is for.
     * @param values double[][] array with data for the x dimension
     * @return itself for method chaining
     */
    public final DataSetBuilder setValues(final int dimIndex, final double[][] values) {
        AssertUtils.nonEmptyArray("values", values);
        AssertUtils.nonEmptyArray("values first col", values[0]);
        int ysize = values.length;
        int xsize = values[0].length;
        final int size = ysize * xsize;
        final double[] vals = new double[size];
        for (int i = 0; i < ysize; i++) {
            AssertUtils.checkArrayDimension("column length", values[i], xsize);
            System.arraycopy(values[i], 0, vals, i * xsize, xsize);
        }
        return setValuesNoCopy(dimIndex, vals);
    }

    /**
     * @param dimIndex the dim index the data is for.
     * @param values double array with data for the x dimension
     * @return itself for method chaining
     */
    public DataSetBuilder setValuesNoCopy(int dimIndex, double[] values) {
        this.values.put(dimIndex, values);
        return this;
    }

    /**
     * @param dimIndex the dim index the data is for.
     * @param values float array with data for the x dimension
     * @return itself for method chaining
     */
    public DataSetBuilder setValuesNoCopy(int dimIndex, float[] values) {
        this.valuesFloat.put(dimIndex, values);
        return this;
    }

    /**
     * @param nDims the number of dimensions of the dataSet to build
     * @return itself for method chaining
     */
    public DataSetBuilder setDimension(final int nDims) {
        this.dimension = nDims;
        return this;
    }

    /**
     * Determines wether an error DataSet should be returned.
     * Note that all setError* functions implicitly set this to true.
     * 
     * @param enableErrors whether to build a data set with errors
     * @return itself for method chaining
     */
    public DataSetBuilder setEnableErrors(boolean enableErrors) {
        this.useErrors = enableErrors;
        return this;
    }

    /**
     * @param useFloat whether to return a float DataSet
     * @return itself for method chaining
     */
    public DataSetBuilder setUseFloat(boolean useFloat) {
        this.useFloat = useFloat;
        return this;
    }

    /**
     * Sets the initial size of the dataset.
     * A value for each dimension can be specified.
     * If the number of dimensions exceeds the given sizes, the last one will be used.
     * This means, that you can use a single value for a data set with the same number of data points in each dimension.
     * 
     * @param newInitialCapacity varArgs sizes for the first n dimensions
     * @return itself for method chaining
     */
    public DataSetBuilder setInitalCapacity(int... newInitialCapacity) {
        initialCapacity = newInitialCapacity;
        return this;
    }

    // Deprecated methods

    /**
     * @param values double array with data for the x dimension
     * @return itself for method chaining
     * @deprecated Use {@code #setValuesNoCopy(DataSet.DIM_X, values)} instead
     */
    @Deprecated
    public DataSetBuilder setXValuesNoCopy(final double[] values) {
        return setValuesNoCopy(DataSet.DIM_X, values);
    }

    /**
     * @param values double array with data for the y dimension
     * @return itself for method chaining
     * @deprecated Use {@code #setValuesNoCopy(DataSet.DIM_Y, values)} instead
     */
    @Deprecated
    public DataSetBuilder setYValuesNoCopy(final double[] values) {
        return setValuesNoCopy(DataSet.DIM_Y, values);
    }

    /**
     * @param values double array with data for the x dimension
     * @return itself for method chaining
     * @deprecated Use {@code #setValues(DataSet.DIM_X, values)} instead
     */
    @Deprecated
    public DataSetBuilder setXValues(final double[] values) {
        return setValues(DataSet.DIM_X, values);
    }

    /**
     * @param values double array with data for the y dimension
     * @return itself for method chaining
     * @deprecated Use {@code #setValues(DataSet.DIM_Y, values)} instead
     */
    @Deprecated
    public DataSetBuilder setYValues(final double[] values) {
        return setValues(DataSet.DIM_Y, values);
    }

    /**
     * @param errors double array with data for the x dimension positive error
     * @return itself for method chaining
     * @deprecated Use {@code #setPosErrorNoCopy(DataSet.DIM_X, values)} instead
     */
    @Deprecated
    public DataSetBuilder setXPosErrorNoCopy(final double[] errors) {
        return setPosError(DataSet.DIM_X, errors);
    }

    /**
     * @param errors double array with data for the y dimension positive error
     * @return itself for method chaining
     * @deprecated Use {@code #setPosErrorNoCopy(DataSet.DIM_Y, values)} instead
     */
    @Deprecated
    public DataSetBuilder setYPosErrorNoCopy(final double[] errors) {
        return setPosError(DataSet.DIM_Y, errors);
    }

    /**
     * @param errors double array with data for the x dimension positive error
     * @return itself for method chaining
     * @deprecated Use {@code #setPosError(DataSet.DIM_X, values)} instead
     */
    @Deprecated
    public DataSetBuilder setXPosError(final double[] errors) {
        return setPosError(DataSet.DIM_X, errors);
    }

    /**
     * @param errors double array with data for the y dimension positive error
     * @return itself for method chaining
     * @deprecated Use {@code #setPosError(DataSet.DIM_Y, values)} instead
     */
    @Deprecated
    public DataSetBuilder setYPosError(final double[] errors) {
        return setPosError(DataSet.DIM_Y, errors);
    }

    @Deprecated
    public DataSetBuilder setXNegErrorNoCopy(final double[] errors) {
        return setNegErrorNoCopy(DataSet.DIM_X, errors);
    }

    @Deprecated
    public DataSetBuilder setYNegErrorNoCopy(final double[] errors) {
        return setNegErrorNoCopy(DataSet.DIM_Y, errors);
    }

    @Deprecated
    public DataSetBuilder setXNegError(final double[] errors) {
        return setNegError(DataSet.DIM_X, errors);
    }

    @Deprecated
    public DataSetBuilder setYNegError(final double[] errors) {
        return setNegError(DataSet.DIM_Y, errors);
    }
}
