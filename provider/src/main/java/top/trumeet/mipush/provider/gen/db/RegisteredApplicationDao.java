package top.trumeet.mipush.provider.gen.db;

import android.database.Cursor;
import android.database.sqlite.SQLiteStatement;

import org.greenrobot.greendao.AbstractDao;
import org.greenrobot.greendao.Property;
import org.greenrobot.greendao.internal.DaoConfig;
import org.greenrobot.greendao.database.Database;
import org.greenrobot.greendao.database.DatabaseStatement;

import top.trumeet.mipush.provider.register.RegisteredApplication;

// THIS CODE IS GENERATED BY greenDAO, DO NOT EDIT.
/** 
 * DAO for table "REGISTERED_APPLICATION".
*/
public class RegisteredApplicationDao extends AbstractDao<RegisteredApplication, Long> {

    public static final String TABLENAME = "REGISTERED_APPLICATION";

    /**
     * Properties of entity RegisteredApplication.<br/>
     * Can be used for QueryBuilder and for referencing column names.
     */
    public static class Properties {
        public final static Property Id = new Property(0, Long.class, "id", true, "_id");
        public final static Property PackageName = new Property(1, String.class, "packageName", false, "pkg");
        public final static Property Type = new Property(2, int.class, "type", false, "type");
        public final static Property AllowReceivePush = new Property(3, boolean.class, "allowReceivePush", false, "allow_receive_push");
        public final static Property AllowReceiveRegisterResult = new Property(4, boolean.class, "allowReceiveRegisterResult", false, "allow_receive_register_result");
        public final static Property AllowReceiveCommand = new Property(5, boolean.class, "allowReceiveCommand", false, "allow_receive_command_without_register_result");
        public final static Property NotificationOnRegister = new Property(6, boolean.class, "notificationOnRegister", false, "notification_on_register");
    }


    public RegisteredApplicationDao(DaoConfig config) {
        super(config);
    }
    
    public RegisteredApplicationDao(DaoConfig config, DaoSession daoSession) {
        super(config, daoSession);
    }

    /** Creates the underlying database table. */
    public static void createTable(Database db, boolean ifNotExists) {
        String constraint = ifNotExists? "IF NOT EXISTS ": "";
        db.execSQL("CREATE TABLE " + constraint + "\"REGISTERED_APPLICATION\" (" + //
                "\"_id\" INTEGER PRIMARY KEY ," + // 0: id
                "\"pkg\" TEXT UNIQUE ," + // 1: packageName
                "\"type\" INTEGER NOT NULL ," + // 2: type
                "\"allow_receive_push\" INTEGER NOT NULL ," + // 3: allowReceivePush
                "\"allow_receive_register_result\" INTEGER NOT NULL ," + // 4: allowReceiveRegisterResult
                "\"allow_receive_command_without_register_result\" INTEGER NOT NULL ," + // 5: allowReceiveCommand
                "\"notification_on_register\" INTEGER NOT NULL );"); // 6: notificationOnRegister
    }

    /** Drops the underlying database table. */
    public static void dropTable(Database db, boolean ifExists) {
        String sql = "DROP TABLE " + (ifExists ? "IF EXISTS " : "") + "\"REGISTERED_APPLICATION\"";
        db.execSQL(sql);
    }

    @Override
    protected final void bindValues(DatabaseStatement stmt, RegisteredApplication entity) {
        stmt.clearBindings();
 
        Long id = entity.getId();
        if (id != null) {
            stmt.bindLong(1, id);
        }
 
        String packageName = entity.getPackageName();
        if (packageName != null) {
            stmt.bindString(2, packageName);
        }
        stmt.bindLong(3, entity.getType());
        stmt.bindLong(4, entity.getAllowReceivePush() ? 1L: 0L);
        stmt.bindLong(5, entity.getAllowReceiveRegisterResult() ? 1L: 0L);
        stmt.bindLong(6, entity.getAllowReceiveCommand() ? 1L: 0L);
        stmt.bindLong(7, entity.getNotificationOnRegister() ? 1L: 0L);
    }

    @Override
    protected final void bindValues(SQLiteStatement stmt, RegisteredApplication entity) {
        stmt.clearBindings();
 
        Long id = entity.getId();
        if (id != null) {
            stmt.bindLong(1, id);
        }
 
        String packageName = entity.getPackageName();
        if (packageName != null) {
            stmt.bindString(2, packageName);
        }
        stmt.bindLong(3, entity.getType());
        stmt.bindLong(4, entity.getAllowReceivePush() ? 1L: 0L);
        stmt.bindLong(5, entity.getAllowReceiveRegisterResult() ? 1L: 0L);
        stmt.bindLong(6, entity.getAllowReceiveCommand() ? 1L: 0L);
        stmt.bindLong(7, entity.getNotificationOnRegister() ? 1L: 0L);
    }

    @Override
    public Long readKey(Cursor cursor, int offset) {
        return cursor.isNull(offset + 0) ? null : cursor.getLong(offset + 0);
    }    

    @Override
    public RegisteredApplication readEntity(Cursor cursor, int offset) {
        RegisteredApplication entity = new RegisteredApplication( //
            cursor.isNull(offset + 0) ? null : cursor.getLong(offset + 0), // id
            cursor.isNull(offset + 1) ? null : cursor.getString(offset + 1), // packageName
            cursor.getInt(offset + 2), // type
            cursor.getShort(offset + 3) != 0, // allowReceivePush
            cursor.getShort(offset + 4) != 0, // allowReceiveRegisterResult
            cursor.getShort(offset + 5) != 0, // allowReceiveCommand
            cursor.getShort(offset + 6) != 0 // notificationOnRegister
        );
        return entity;
    }
     
    @Override
    public void readEntity(Cursor cursor, RegisteredApplication entity, int offset) {
        entity.setId(cursor.isNull(offset + 0) ? null : cursor.getLong(offset + 0));
        entity.setPackageName(cursor.isNull(offset + 1) ? null : cursor.getString(offset + 1));
        entity.setType(cursor.getInt(offset + 2));
        entity.setAllowReceivePush(cursor.getShort(offset + 3) != 0);
        entity.setAllowReceiveRegisterResult(cursor.getShort(offset + 4) != 0);
        entity.setAllowReceiveCommand(cursor.getShort(offset + 5) != 0);
        entity.setNotificationOnRegister(cursor.getShort(offset + 6) != 0);
     }
    
    @Override
    protected final Long updateKeyAfterInsert(RegisteredApplication entity, long rowId) {
        entity.setId(rowId);
        return rowId;
    }
    
    @Override
    public Long getKey(RegisteredApplication entity) {
        if(entity != null) {
            return entity.getId();
        } else {
            return null;
        }
    }

    @Override
    public boolean hasKey(RegisteredApplication entity) {
        return entity.getId() != null;
    }

    @Override
    protected final boolean isEntityUpdateable() {
        return true;
    }
    
}
