/*
 * Copyright (c) 2013, OpenCloudDB/MyCAT and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software;Designed and Developed mainly by many Chinese 
 * opensource volunteers. you can redistribute it and/or modify it under the 
 * terms of the GNU General Public License version 2 only, as published by the
 * Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 * 
 * Any questions about this component can be directed to it's project Web address 
 * https://code.google.com/p/opencloudb/.
 *
 */
package org.opencloudb.response;

import java.nio.ByteBuffer;
import java.util.Map;

import org.opencloudb.config.Fields;
import org.opencloudb.manager.ManagerConnection;
import org.opencloudb.mysql.PacketUtil;
import org.opencloudb.net.mysql.EOFPacket;
import org.opencloudb.net.mysql.FieldPacket;
import org.opencloudb.net.mysql.ResultSetHeaderPacket;
import org.opencloudb.net.mysql.RowDataPacket;
import org.opencloudb.stat.UserStat;
import org.opencloudb.stat.UserStatAnalyzer;
import org.opencloudb.statistic.SQLRecord;
import org.opencloudb.util.LongUtil;
import org.opencloudb.util.StringUtil;

/**
 * 查询每个用户的执行时间超过设定阈值的SQL, 默认TOP10
 * 
 * @author mycat
 * @author zhuam
 */
public final class ShowSQLSlow {

    private static final int FIELD_COUNT = 6;
    private static final ResultSetHeaderPacket header = PacketUtil.getHeader(FIELD_COUNT);
    private static final FieldPacket[] fields = new FieldPacket[FIELD_COUNT];
    private static final EOFPacket eof = new EOFPacket();
    
    static {
        int i = 0;
        byte packetId = 0;
        header.packetId = ++packetId;
        
        fields[i] = PacketUtil.getField("USER", Fields.FIELD_TYPE_VAR_STRING);
        fields[i++].packetId = ++packetId;

        fields[i] = PacketUtil.getField("DATASOURCE", Fields.FIELD_TYPE_VAR_STRING);
        fields[i++].packetId = ++packetId;

        fields[i] = PacketUtil.getField("START_TIME", Fields.FIELD_TYPE_LONGLONG);
        fields[i++].packetId = ++packetId;

        fields[i] = PacketUtil.getField("EXECUTE_TIME", Fields.FIELD_TYPE_LONGLONG);
        fields[i++].packetId = ++packetId;

        fields[i] = PacketUtil.getField("SQL", Fields.FIELD_TYPE_VAR_STRING);
        fields[i++].packetId = ++packetId;
        
        fields[i] = PacketUtil.getField("IP", Fields.FIELD_TYPE_VAR_STRING);
        fields[i++].packetId = ++packetId;  
        
        eof.packetId = ++packetId;
    }

    public static void execute(ManagerConnection c, boolean isClear) {
        ByteBuffer buffer = c.allocate();

        // write header
        buffer = header.write(buffer, c,true);

        // write fields
        for (FieldPacket field : fields) {
            buffer = field.write(buffer, c,true);
        }

        // write eof
        buffer = eof.write(buffer, c,true);

        // write rows
        byte packetId = eof.packetId;        
        Map<String, UserStat> statMap = UserStatAnalyzer.getInstance().getUserStatMap();
        for (UserStat userStat : statMap.values()) {
        	String user = userStat.getUser();
            SQLRecord[] records = userStat.getSqlRecorder().getRecords();
            for (int i = records.length - 1; i >= 0; i--) {
                if (records[i] != null) {
                    RowDataPacket row = getRow(user, records[i], c.getCharset());
                    row.packetId = ++packetId;
                    buffer = row.write(buffer, c,true);
                }
            }
            
            if ( isClear ) {
            	userStat.getSqlRecorder().clear();//读取慢SQL后，清理
            }
        }

        // write last eof
        EOFPacket lastEof = new EOFPacket();
        lastEof.packetId = ++packetId;
        buffer = lastEof.write(buffer, c,true);

        // write buffer
        c.write(buffer);
    }

    private static RowDataPacket getRow(String user, SQLRecord sql, String charset) {
        RowDataPacket row = new RowDataPacket(FIELD_COUNT);
        row.add( StringUtil.encode(user, charset) );
        row.add( StringUtil.encode(sql.dataNode, charset) );
        row.add( LongUtil.toBytes(sql.startTime) );
        row.add( LongUtil.toBytes(sql.executeTime) );
        row.add( StringUtil.encode(sql.statement, charset) );
        row.add( StringUtil.encode(sql.host, charset) );
        return row;
    }

}