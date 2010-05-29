package com.metaweb.gridworks.browsing.facets;

import java.util.Properties;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONWriter;

import com.metaweb.gridworks.browsing.FilteredRows;
import com.metaweb.gridworks.browsing.filters.ExpressionNumberComparisonRowFilter;
import com.metaweb.gridworks.browsing.filters.RowFilter;
import com.metaweb.gridworks.expr.Evaluable;
import com.metaweb.gridworks.expr.MetaParser;
import com.metaweb.gridworks.expr.ParsingException;
import com.metaweb.gridworks.model.Column;
import com.metaweb.gridworks.model.Project;
import com.metaweb.gridworks.util.JSONUtilities;

public class RangeFacet implements Facet {
    /*
     * Configuration, from the client side
     */
    protected String     _name;       // name of facet
    protected String     _expression; // expression to compute numeric value(s) per row
    protected String     _columnName; // column to base expression on, if any
    protected String     _mode;       // "range", "min", "max"
    
    protected double    _from; // the numeric selection
    protected double    _to;
    
    protected boolean   _selectNumeric; // whether the numeric selection applies, default true
    protected boolean   _selectNonNumeric;
    protected boolean   _selectBlank;
    protected boolean   _selectError;
    
    /*
     * Derived configuration data
     */
    protected int        _cellIndex;
    protected Evaluable  _eval;
    protected String     _errorMessage;
    protected boolean    _selected; // false if we're certain that all rows will match
                                    // and there isn't any filtering to do
    
    /*
     * Computed data, to return to the client side
     */
    protected double    _min;
    protected double    _max;
    protected double    _step;
    protected int[]     _baseBins;
    protected int[]     _bins;
    
    protected int       _numericCount;
    protected int       _nonNumericCount;
    protected int       _blankCount;
    protected int       _errorCount;
    
    public RangeFacet() {
    }

    public void write(JSONWriter writer, Properties options)
            throws JSONException {
        
        writer.object();
        writer.key("name"); writer.value(_name);
        writer.key("expression"); writer.value(_expression);
        writer.key("columnName"); writer.value(_columnName);
        writer.key("mode"); writer.value(_mode);
        
        if (_errorMessage != null) {
            writer.key("error"); writer.value(_errorMessage);
        } else {
            if (!Double.isInfinite(_min) && !Double.isInfinite(_max)) {
                writer.key("min"); writer.value(_min);
                writer.key("max"); writer.value(_max);
                writer.key("step"); writer.value(_step);
                
                writer.key("bins"); writer.array();
                for (int b : _bins) {
                    writer.value(b);
                }
                writer.endArray();
                
                writer.key("baseBins"); writer.array();
                for (int b : _baseBins) {
                    writer.value(b);
                }
                writer.endArray();
                
                if ("min".equals(_mode)) {
                    writer.key("from"); writer.value(_from);
                } else if ("max".equals(_mode)) {
                    writer.key("to"); writer.value(_to);
                } else {
                    writer.key("from"); writer.value(_from);
                    writer.key("to"); writer.value(_to);
                }
            }
            
            writer.key("numericCount"); writer.value(_numericCount);
            writer.key("nonNumericCount"); writer.value(_nonNumericCount);
            writer.key("blankCount"); writer.value(_blankCount);
            writer.key("errorCount"); writer.value(_errorCount);
        }
        writer.endObject();
    }

    public void initializeFromJSON(Project project, JSONObject o) throws Exception {
        _name = o.getString("name");
        _expression = o.getString("expression");
        _columnName = o.getString("columnName");
        
        if (_columnName.length() > 0) {
            Column column = project.columnModel.getColumnByName(_columnName);
            if (column != null) {
                _cellIndex = column.getCellIndex();
            } else {
                _errorMessage = "No column named " + _columnName;
            }
        } else {
            _cellIndex = -1;
        }
        
        try {
            _eval = MetaParser.parse(_expression);
        } catch (ParsingException e) {
            _errorMessage = e.getMessage();
        }
        
        _mode = o.getString("mode");
        if ("min".equals(_mode)) {
            if (o.has("from")) {
                _from = o.getDouble("from");
                _selected = true;
            }
        } else if ("max".equals(_mode)) {
            if (o.has("to")) {
                _to = o.getDouble("to");
                _selected = true;
            }
        } else {
            if (o.has("from") && o.has("to")) {
                _from = o.getDouble("from");
                _to = o.getDouble("to");
                _selected = true;
            }
        }
        
        _selectNumeric = JSONUtilities.getBoolean(o, "selectNumeric", true);
        _selectNonNumeric = JSONUtilities.getBoolean(o, "selectNonNumeric", true);
        _selectBlank = JSONUtilities.getBoolean(o, "selectBlank", true);
        _selectError = JSONUtilities.getBoolean(o, "selectError", true);
        
        if (!_selectNumeric || !_selectNonNumeric || !_selectBlank || !_selectError) {
            _selected = true;
        }
    }

    public RowFilter getRowFilter() {
        if (_eval != null && _errorMessage == null && _selected) {
            if ("min".equals(_mode)) {
                return new ExpressionNumberComparisonRowFilter(
                        _eval, _columnName, _cellIndex, _selectNumeric, _selectNonNumeric, _selectBlank, _selectError) {
                    
                    protected boolean checkValue(double d) {
                        return d >= _from;
                    };
                };
            } else if ("max".equals(_mode)) {
                return new ExpressionNumberComparisonRowFilter(
                        _eval, _columnName, _cellIndex, _selectNumeric, _selectNonNumeric, _selectBlank, _selectError) {
                    
                    protected boolean checkValue(double d) {
                        return d < _to;
                    };
                };
            } else {
                return new ExpressionNumberComparisonRowFilter(
                        _eval, _columnName, _cellIndex, _selectNumeric, _selectNonNumeric, _selectBlank, _selectError) {
                    
                    protected boolean checkValue(double d) {
                        return d >= _from && d < _to;
                    };
                };
            }        
        } else {
            return null;
        }
    }

    public void computeChoices(Project project, FilteredRows filteredRows) {
        if (_eval != null && _errorMessage == null) {
            Column column = project.columnModel.getColumnByCellIndex(_cellIndex);
            
            String key = "numeric-bin:" + _expression;
            NumericBinIndex index = (NumericBinIndex) column.getPrecompute(key);
            if (index == null) {
                index = new NumericBinIndex(project, _columnName, _cellIndex, _eval);
                column.setPrecompute(key, index);
            }
            
            _min = index.getMin();
            _max = index.getMax();
            _step = index.getStep();
            _baseBins = index.getBins();
            
            if (_selected) {
                _from = Math.max(_from, _min);
                _to = Math.min(_to, _max);
            } else {
                _from = _min;
                _to = _max;
            }
            
            ExpressionNumericRowBinner binner = 
                new ExpressionNumericRowBinner(_eval, _columnName, _cellIndex, index);
            
            filteredRows.accept(project, binner);
            
            _bins = binner.bins;
            _numericCount = binner.numericCount;
            _nonNumericCount = binner.nonNumericCount;
            _blankCount = binner.blankCount;
            _errorCount = binner.errorCount;
        }
    }
}