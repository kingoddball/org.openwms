/*
 * openwms.org, the Open Warehouse Management System.
 *
 * This file is part of openwms.org.
 *
 * openwms.org is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as 
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * openwms.org is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software. If not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.openwms.persistence.ext.hibernate;

import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.hibernate.Hibernate;
import org.hibernate.HibernateException;
import org.hibernate.TypeMismatchException;
import org.hibernate.engine.SessionImplementor;
import org.hibernate.type.Type;
import org.hibernate.usertype.CompositeUserType;
import org.openwms.common.domain.units.Piece;
import org.openwms.common.domain.units.PieceUnit;
import org.openwms.common.domain.units.Weight;
import org.openwms.common.domain.units.WeightUnit;
import org.openwms.core.domain.values.Unit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An UnitUserType is used by Hibernate as converter for custom
 * <code>Unit</code> types. Only subclasses of {@link Unit} are supported by
 * this type converter.
 * 
 * @author <a href="mailto:scherrer@openwms.org">Heiko Scherrer</a>
 * @version $Revision: $
 * @since 0.2
 */
public class UnitUserType implements CompositeUserType {

    private static final Logger LOGGER = LoggerFactory.getLogger(UnitUserType.class);

    /**
     * {@inheritDoc}
     * 
     * We expect that every unit has two fields, named <code>unitType</code> and
     * <code>amount</code>.
     */
    @Override
    public String[] getPropertyNames() {
        return new String[] { "unitType", "amount" };
    }

    /**
     * {@inheritDoc}
     * 
     * We're going to persist both fields as Strings.
     */
    @Override
    public Type[] getPropertyTypes() {
        return new Type[] { Hibernate.STRING, Hibernate.STRING };
    }

    /**
     * {@inheritDoc}
     * 
     * @throws HibernateException
     *             in case of errors
     */
    @Override
    public Object getPropertyValue(Object component, int property) throws HibernateException {
        if (component instanceof Piece) {
            Piece piece = (Piece) component;
            return property == 0 ? piece.getUnitType() : piece.getAmount();
        } else if (component instanceof Weight) {
            Weight weight = (Weight) component;
            return property == 0 ? weight.getUnitType() : weight.getAmount();
        }
        throw new TypeMismatchException("Incompatible type:" + component.getClass());
    }

    /**
     * {@inheritDoc}
     * 
     * We have immutable types, throw an UnsupportedOperationException here.
     * 
     * @throws HibernateException
     *             in case of errors
     */
    @Override
    public void setPropertyValue(Object component, int property, Object value) throws HibernateException {
        throw new UnsupportedOperationException("Unit types are immutable");
    }

    /**
     * {@inheritDoc}
     * 
     * We do not know the concrete implementation here and return an Unit class
     * type.
     */
    @Override
    public Class returnedClass() {
        return Unit.class;
    }

    /**
     * {@inheritDoc}
     * 
     * Delegate to Unit implementation.
     * 
     * @throws HibernateException
     *             in case of errors
     */
    @Override
    public boolean equals(Object x, Object y) throws HibernateException {
        if (x == y) {
            return true;
        }
        if (x == null || y == null) {
            return false;
        }
        return x.equals(y);
    }

    /**
     * {@inheritDoc}
     * 
     * Delegate to Unit implementation.
     * 
     * @throws HibernateException
     *             in case of errors
     */
    @Override
    public int hashCode(Object x) throws HibernateException {
        return x.hashCode();
    }

    /**
     * {@inheritDoc}
     * 
     * Try to re-assign the value read from the database to some type of Unit.
     * Currently supported types:
     * <ul>
     * <li>Piece</li>
     * <li>Weight</li>
     * </ul>
     * 
     * @throws HibernateException
     *             in case of errors
     * @throws SQLException
     *             in case of database errors
     */
    @Override
    public Object nullSafeGet(ResultSet rs, String[] names, SessionImplementor session, Object owner)
            throws HibernateException, SQLException {
        String rs0 = rs.getString(names[0]);
        if (rs.wasNull()) {
            return null;
        }
        String[] val = rs0.split("@");
        String unitType = val[0];
        String unitTypeClass = val[1];
        if (Piece.class.getCanonicalName().equals(unitTypeClass)) {
            int amount = rs.getInt(names[1]);
            return new Piece(amount, PieceUnit.valueOf(unitType));
        } else if (Weight.class.getCanonicalName().equals(unitTypeClass)) {
            BigDecimal amount = rs.getBigDecimal(names[1]);
            return new Weight(amount, WeightUnit.valueOf(unitType));
        }
        throw new TypeMismatchException("Incompatible type: " + unitTypeClass);
    }

    /**
     * {@inheritDoc}
     * 
     * We've to store the concrete classname as well.
     * 
     * @throws HibernateException
     *             in case of errors
     * @throws SQLException
     *             in case of database errors
     */
    @Override
    public void nullSafeSet(PreparedStatement st, Object value, int index, SessionImplementor session)
            throws HibernateException, SQLException {
        if (value == null) {
            st.setNull(index, Hibernate.STRING.sqlType());
            st.setNull(index + 1, Hibernate.STRING.sqlType());
        } else {
            if (value instanceof Piece) {
                Piece piece = (Piece) value;
                st.setString(index, piece.getUnitType().toString() + "@" + Piece.class.getCanonicalName());
                st.setString(index + 1, piece.getAmount().toPlainString());
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Binding '" + piece.getUnitType().toString() + "@" + Piece.class.getCanonicalName()
                            + "' to parameter: " + index);
                    LOGGER.trace("Binding '" + piece.getAmount().toPlainString() + "' to parameter: " + (index + 1));
                }
            } else if (value instanceof Weight) {
                Weight weight = (Weight) value;
                st.setString(index, weight.getUnitType().toString() + "@" + Weight.class.getCanonicalName());
                st.setString(index + 1, weight.getAmount().toPlainString());
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Binding '" + weight.getUnitType().toString() + "@" + Weight.class.getCanonicalName()
                            + "' to parameter: " + index);
                    LOGGER.trace("Binding '" + weight.getAmount().toPlainString() + "' to parameter: " + index + 1);
                }
            } else {
                throw new TypeMismatchException("Incompatible type: " + value.getClass().getCanonicalName());
            }
        }
    }

    /**
     * {@inheritDoc}
     * 
     * No deep copy -> Immutable types.
     * 
     * @throws HibernateException
     *             in case of errors
     */
    @Override
    public Object deepCopy(Object value) throws HibernateException {
        return value;
    }

    /**
     * {@inheritDoc}
     * 
     * All Unit types aren't mutable.
     */
    @Override
    public boolean isMutable() {
        return false;
    }

    /**
     * {@inheritDoc}
     * 
     * Just cast.
     * 
     * @throws HibernateException
     *             in case of errors
     */
    @Override
    public Serializable disassemble(Object value, SessionImplementor session) throws HibernateException {
        return (Serializable) value;
    }

    /**
     * {@inheritDoc}
     * 
     * @throws HibernateException
     *             in case of errors
     */
    @Override
    public Object assemble(Serializable cached, SessionImplementor session, Object owner) throws HibernateException {
        return cached;
    }

    /**
     * {@inheritDoc}
     * 
     * @throws HibernateException
     *             in case of errors
     */
    @Override
    public Object replace(Object original, Object target, SessionImplementor session, Object owner)
            throws HibernateException {
        return original;
    }
}